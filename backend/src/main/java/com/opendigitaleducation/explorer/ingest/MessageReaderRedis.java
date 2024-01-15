package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.redis.RedisClient;

import java.util.*;
import java.util.function.Function;

public class MessageReaderRedis implements MessageReader {
    static final Logger log = LoggerFactory.getLogger(MessageReaderRedis.class);
    static final Integer DEFAULT_BLOCK_MS = 0;//infinity
    static final Integer DEFAULT_RETRY_READ_MS = 1000;//infinity
    static final String DEFAULT_CONSUMER_NAME = "message_reader";
    static final String DEFAULT_STREAM_FAIL = "_fail";
    static final String DEFAULT_CONSUMER_GROUP = "message_reader_group";
    private final Vertx vertx;
    private final String consumerName;
    private final String consumerGroup;
    private final String streamFailSuffix;
    private final Integer consumerBlockMs;
    private final Integer retryReadMs;
    private final Future<Void> onReady;
    private final RedisClient redisClient;
    private final List<String> streams = new ArrayList<>();
    private final List<Handler<Void>> listeners = new ArrayList<>();
    private final JsonObject metrics = new JsonObject();
    private int pendingNotifications = 0;
    private boolean listening = false;
    private Long retryTimer;
    private MessageReaderStatus status = MessageReaderStatus.Running;

    public MessageReaderRedis(final Vertx vertx, final RedisClient redisClient, final JsonObject config) {
        this.vertx = vertx;
        this.redisClient = redisClient;
        this.retryReadMs = config.getInteger("retry-read-ms", DEFAULT_RETRY_READ_MS);
        this.consumerBlockMs = config.getInteger("consumer-block-ms", DEFAULT_BLOCK_MS);
        this.streamFailSuffix = config.getString("stream-fail-suffix", DEFAULT_STREAM_FAIL);
        this.consumerName = config.getString("consumer-name", DEFAULT_CONSUMER_NAME);
        this.consumerGroup = config.getString("consumer-group", DEFAULT_CONSUMER_GROUP);
        final JsonArray streams = config.getJsonArray("streams", ExplorerPluginCommunicationRedis.DEFAULT_STREAMS);
        //order by priority DESC
        final List<String> initStreams = new ArrayList<>();
        for (final Object stream : streams) {
            this.streams.add(stream.toString());
            //stream to init
            initStreams.add(stream.toString());
            initStreams.add(stream + streamFailSuffix);
        }
        if (this.streams.isEmpty()) {
            log.error("Missing streams list");
        }
        onReady = redisClient.xcreateGroup(consumerGroup, initStreams).onComplete(e -> {
            if(e.failed()){
                log.error("Could not create redis group: " + consumerGroup, e.cause());
            }
        });
    }

    protected boolean notifyListeners() {
        if (pendingNotifications > 0) {
            //notify listeners
            final List<Handler<Void>> copy = new ArrayList<>(listeners);
            for (final Handler<Void> listener : copy) {
                listener.handle(null);
            }
            this.pendingNotifications = 0;
            return true;
        }
        return false;
    }

    protected void scheduleXread() {
        if (isStopped()) {
            return;
        }
        if (listening) {
            return;
        }
        listening = true;
        onReady.onSuccess(e -> {
            //only new messages
            final String startFrom = ">";
            redisClient.xreadGroup(consumerGroup, consumerName, streams, true, Optional.of(1), Optional.of(consumerBlockMs), Optional.of(startFrom),true).onComplete(res -> {
                try {
                    if (res.failed()) {
                        log.error("Could not read xstream ", res.cause());
                        return;
                    }
                    this.listening = false;
                    if (res.result().size() > 0) {
                        this.pendingNotifications++;
                    }
                    //do not notify each time
                    if (isStopped()) {
                        metrics.put("last_listen_skip_at", new Date().getTime());
                        metrics.put("listen_skip_count", metrics.getInteger("listen_skip_count", 0) + 1);
                    } else {
                        metrics.put("last_listen_at", new Date().getTime());
                        metrics.put("listen_count", metrics.getInteger("listen_count", 0) + 1);
                    }
                    //call listeners (avoid concurrent modification
                    notifyListeners();
                } finally {
                    if(res.failed()){
                        // if redis is down retry later
                        //TODO add circuit breaker + exponential delay inside RedisClient?
                        if(this.retryTimer != null){
                            vertx.cancelTimer(this.retryTimer);
                        }
                        this.retryTimer = vertx.setTimer(this.retryReadMs, (Long time)->{
                            //if paused on notify => stop listening => while rerun on resume
                            scheduleXread();
                        });
                    }else{
                        //if paused on notify => stop listening => while rerun on resume
                        scheduleXread();
                    }
                }
            });
        });
    }


    @Override
    public Function<Void, Void> listenNewMessages(final Handler<Void> handler) {
        listeners.add(handler);
        return e -> {
            listeners.remove(handler);
            return null;
        };
    }

    @Override
    public MessageReaderStatus getStatus() {
        return status;
    }

    @Override
    public void stop() {
        status = MessageReaderStatus.Stopped;
        if(this.retryTimer != null){
            vertx.cancelTimer(this.retryTimer);
        }
    }

    @Override
    public Future<Void> start() {
        status = MessageReaderStatus.Running;
        //schedule listen
        scheduleXread();
        return onReady;
    }

    protected Future<List<JsonObject>> fetchOneStream(final String stream, int maxBatchSize, boolean pending) {
        return onReady.compose(e->{
            final Promise<List<JsonObject>> promise = Promise.promise();
            final String startAt = pending ? "0" : ">";
            redisClient.xreadGroup(consumerGroup, consumerName, stream, true, Optional.of(maxBatchSize), Optional.empty(), Optional.of(startAt), true).onComplete(res -> {
                if (res.succeeded()) {
                    promise.complete(res.result());
                } else {
                    log.error(String.format("Could not xread (%s,%s) from streams: %s | index=%s", consumerGroup, consumerName, stream, startAt), res.cause());
                    promise.fail(res.cause());
                }
            });
            return promise.future();
        });
    }

    protected Future<List<ExplorerMessageForIngest>> fetchAllStreams(final List<JsonObject> result, final int maxBatchSize, final Optional<String> suffix, final Optional<Integer> maxAttempt) {
        //iterate over streams by priority and fill results list
        final Iterator<String> it = streams.iterator();
        Future<List<JsonObject>> futureIt = Future.succeededFuture(new ArrayList<>());
        do {
            final String nextStream = it.next() + suffix.orElse("");
            //fetch pending first
            futureIt = futureIt.compose(r -> {
                result.addAll(r);
                final int newMaxBatchSize = maxBatchSize - result.size();
                if (newMaxBatchSize > 0) {
                    return fetchOneStream(nextStream, newMaxBatchSize, true);
                } else {
                    return Future.succeededFuture(new ArrayList<>());
                }
            });
            //fetch new then
            futureIt = futureIt.compose(r -> {
                result.addAll(r);
                final int newMaxBatchSize = maxBatchSize - result.size();
                if (newMaxBatchSize > 0) {
                    return fetchOneStream(nextStream, newMaxBatchSize, false);
                } else {
                    return Future.succeededFuture(new ArrayList<>());
                }
            });
        } while (it.hasNext());
        return futureIt.map(r -> {
            result.addAll(r);
            //metrics
            metrics.put("last_fetch_max_attempt", maxAttempt.orElse(0));
            metrics.put("last_fetch_max_batch_size", maxBatchSize);
            metrics.put("last_fetch_at", new Date().getTime());
            metrics.put("last_fetch_size", r.size());
            metrics.put("fetch_count", metrics.getInteger("fetch_count", 0) + 1);
            return toMessage(result);
        });
    }

    protected List<ExplorerMessageForIngest> toMessage(final List<JsonObject> result) {
        final List<ExplorerMessageForIngest> messages = new ArrayList<>();
        for (final JsonObject row : result) {
            final String resourceAction = row.getString("resource_action");
            final String idQueue = row.getString(RedisClient.ID_STREAM);
            final String nameStream = row.getString(RedisClient.NAME_STREAM);
            final String idResource = row.getString("id_resource");
            final Integer attemptCount = Integer.valueOf(row.getString("attempt_count", "0"));
            final JsonObject json = new JsonObject(row.getString("payload"));
            final ExplorerMessageForIngest message = new ExplorerMessageForIngest(resourceAction, idQueue, idResource, json);
            message.getMetadata().put(RedisClient.NAME_STREAM, nameStream);
            message.setAttemptCount(attemptCount);
            messages.add(message);
        }
        return messages;
    }

    protected JsonObject toJson(final ExplorerMessageForIngest message) {
        final JsonObject json = new JsonObject();
        json.put("resource_action", message.getAction());
        json.put("id_resource", message.getId());
        json.put("payload", message.getMessage().encode());
        return json;
    }

    @Override
    public Future<List<ExplorerMessageForIngest>> getIncomingMessages(final int maxBatchSize) {
        return fetchAllStreams(new ArrayList<>(), maxBatchSize, Optional.empty(), Optional.empty());
    }

    @Override
    public Future<List<ExplorerMessageForIngest>> getFailedMessages(final int maxBatchSize, final int maxAttempt) {
        return fetchAllStreams(new ArrayList<>(), maxBatchSize, Optional.of(this.streamFailSuffix), Optional.of(maxAttempt));
    }

    @Override
    public Future<Void> updateStatus(final IngestJob.IngestJobResult ingestResult, final int maxAttempt) {
        final Set<String> processed = new HashSet<>();
        //prepare
        final List<Future> transactions = new ArrayList<>();
        //on succeed => ACK + DEL (DEL only if ACK succeed)
        //if we want transaction we cannot push all ids to ack or delete CMD at once
        final List<ExplorerMessageForIngest> toAck = new ArrayList<>(ingestResult.succeed);
        // We also acknowledge skipped messages because we know that
        // they are not true failures and replaying them won't make
        // them pass
        toAck.addAll(ingestResult.skipped);
        for (final ExplorerMessageForIngest mess : toAck) {
            if(mess.getIdQueue().isPresent() && !processed.contains(mess.getIdQueue().get())) {
                final String idQueue = mess.getIdQueue().get();
                final String stream = mess.getMetadata().getString(RedisClient.NAME_STREAM);
                processed.add(idQueue);
                // ACK message then delete it
                transactions.add(redisClient.xAck(stream, consumerGroup, idQueue).compose(onAck -> {
                    return redisClient.xDel(stream, idQueue);
                }));
            }
        }
        //on failed => ADD + ACK + DEL (ACK only if ADD suceed and DEL only if ACK succeed)
        for (final ExplorerMessageForIngest mess : ingestResult.failed) {
            if(mess.getIdQueue().isPresent() && !processed.contains(mess.getIdQueue().get())) {
                final String idQueue = mess.getIdQueue().get();
                final String stream = mess.getMetadata().getString(RedisClient.NAME_STREAM, "");
                final int attemptCount = mess.getAttemptCount();
                processed.add(idQueue);
                if(attemptCount > maxAttempt) {
                    log.warn("A message has been dropped because it was attempted " + attemptCount + " : " + mess);
                    transactions.add(redisClient.xAck(stream, consumerGroup, idQueue).compose(onAck -> {
                        return redisClient.xDel(stream, idQueue);
                    }));
                } else {
                    final JsonObject json = toJson(mess).put("attempt_count", attemptCount + 1)
                            .put("attempted_at", new Date().getTime())
                            .put("error", mess.getError());
                    //if already failed => do not add suffix to stream name
                    final String targetStream = stream.contains(streamFailSuffix)? stream : stream + streamFailSuffix;
                    // add message to failed stream
                    transactions.add(redisClient.xAdd(targetStream, json).compose(onAdd->{
                        // ack message from old stream
                        return redisClient.xAck(stream, consumerGroup, idQueue).compose(onAck -> {
                            // del message from old stream
                            return redisClient.xDel(stream, idQueue);
                        });
                    }));
                }
            }
        }
        //execute batch
        return CompositeFuture.all(transactions).onFailure(e -> {
            log.error("Could not update resource status on queue: ", e);
        }).mapEmpty();
    }

    @Override
    public Future<JsonObject> getMetrics() {
        final JsonObject metrics = this.metrics.copy();
        final List<Future> futures = new ArrayList<>();
        for (final String stream : this.streams) {
            futures.add(redisClient.xInfo(stream).onSuccess(info -> {
                metrics.put("stream_" + stream + "_info", info);
            }));
            futures.add(redisClient.xInfo(stream + streamFailSuffix).onSuccess(info -> {
                metrics.put("stream_" + stream + streamFailSuffix + "_info", info);
            }));
        }
        //TODO age of first and last foreach stream and reformat stream infos
        return CompositeFuture.all(futures).map(e -> {
            return metrics;
        }).otherwise(th -> {
            log.error("Cannot gather metrics", th);
            return metrics;
        });
    }
}
