package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.redis.RedisClient;
import com.opendigitaleducation.explorer.services.impl.ExplorerServiceRedis;
import io.reactiverse.pgclient.PgRowSet;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.function.Function;

public class MessageReaderRedis implements MessageReader {
    static final Logger log = LoggerFactory.getLogger(MessageReaderRedis.class);
    static final String ATTEMPT_COUNT = "attempt_count";
    static final Integer DEFAULT_BLOCK_MS = 0;//infinity
    static final String DEFAULT_CONSUMER_NAME = "message_reader";
    static final String DEFAULT_STREAM_FAIL = "_fail";
    static final String DEFAULT_CONSUMER_GROUP = "message_reader_group";
    private final String consumerName;
    private final String consumerGroup;
    private final String streamFailSuffix;
    private final Integer consumerBlockMs;
    private final Future<Void> onReady;
    private final RedisClient redisClient;
    private final List<String> streams = new ArrayList<>();
    private final List<Handler<Void>> listeners = new ArrayList<>();
    private final List<JsonObject> pending = new ArrayList<>();
    private final JsonObject metrics = new JsonObject();
    private MessageReaderStatus status = MessageReaderStatus.Running;

    public MessageReaderRedis(final RedisClient redisClient, final JsonObject config) {
        this.redisClient = redisClient;
        this.consumerBlockMs = config.getInteger("consumer-block-ms", DEFAULT_BLOCK_MS);
        this.streamFailSuffix = config.getString("stream-fail-suffix", DEFAULT_STREAM_FAIL);
        this.consumerName = config.getString("consumer-name", DEFAULT_CONSUMER_NAME);
        this.consumerGroup = config.getString("consumer-group", DEFAULT_CONSUMER_GROUP);
        final JsonArray streams = config.getJsonArray("streams", ExplorerServiceRedis.DEFAULT_STREAMS);
        for (final Object stream : streams) {
            this.streams.add(stream.toString());
        }
        if (this.streams.isEmpty()) {
            log.error("Missing streams list");
        }
        onReady = redisClient.xcreateGroup(consumerGroup, this.streams).onFailure(e -> {
            log.error("Could not create redis group: " + consumerGroup, e.getCause());
        });
    }

    protected boolean notifyListeners(){
        if(pending.size() > 0){
            //notify listeners
            final List<Handler<Void>> copy = new ArrayList<>(listeners);
            for(final Handler<Void> listener : copy){
                listener.handle(null);
            }
            return true;
        }
        return false;
    }

    protected void scheduleXread(boolean force) {
        //if non empty list => notifylistener
        if(notifyListeners()){
            return;
        }
        onReady.onSuccess(e -> {
            //if no listeners no need to listen
            if (listeners.isEmpty()) {
                return;
            }
            //if already listening no need to trigger
            if (isPaused() && !force) {
                metrics.put("last_listen_skip_at", new Date().getTime());
                metrics.put("listen_skip_count", metrics.getInteger("listen_skip_count", 0)+1);
                return;
            }
            redisClient.xreadGroup(consumerGroup, consumerName, streams, false, Optional.of(1), Optional.of(consumerBlockMs)).onComplete(res -> {
                if (res.succeeded()) {
                    pending.addAll(res.result());
                    notifyListeners();
                } else {
                    log.error(String.format("Could not xread (%s,%s) from streams: %s", consumerGroup, consumerName, streams), res.cause());
                }
                metrics.put("last_listen_at", new Date().getTime());
                metrics.put("listen_count", metrics.getInteger("listen_count", 0)+1);
                if(pending.isEmpty()) {
                    scheduleXread(false);
                }
            });
        });
    }

    protected List<JsonObject> cleanPending() {
        final List<JsonObject> result = new ArrayList<>();
        result.addAll(pending);
        pending.clear();
        return result;
    }

    @Override
    public Function<Void, Void> listenNewMessages(Handler<Void> handler) {
        listeners.add(handler);
        scheduleXread(false);
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
    public void pause() {
        status = MessageReaderStatus.Paused;
    }

    @Override
    public void resume() {
        if(this.pending.size() > 0){
            notifyListeners();
        }
        status = MessageReaderStatus.Running;
    }

    protected Future<List<JsonObject>> fetchOneStream(final String stream, int maxBatchSize) {
        final Promise<List<JsonObject>> promise = Promise.promise();
        redisClient.xreadGroup(consumerGroup, consumerName, stream, true, Optional.of(maxBatchSize), Optional.empty()).onComplete(res -> {
            if (res.succeeded()) {
                promise.complete(res.result());
            } else {
                log.error(String.format("Could not xread (%s,%s) from streams: %s", consumerGroup, consumerName, stream), res.cause());
            }
        });
        return promise.future();
    }

    protected Future<List<MessageIngester.Message>> fetchAllStreams(final List<JsonObject> result, final int maxBatchSize, final Optional<String> suffix, final Optional<Integer> maxAttempt) {
        //iterate over streams by priority and fill results list
        final Iterator<String> it = streams.iterator();
        final int newMaxBatchSizeFirst = maxBatchSize - result.size();
        final String firstStream = it.next() + suffix.orElse("");
        Future<List<JsonObject>> futureIt = fetchOneStream(firstStream, newMaxBatchSizeFirst);
        while (it.hasNext()) {
            futureIt = futureIt.compose(r -> {
                result.addAll(r);
                final String nextStream = it.next() + suffix.orElse("");
                final int newMaxBatchSize = maxBatchSize - result.size();
                if (newMaxBatchSize > 0) {
                    return fetchOneStream(nextStream, maxBatchSize);
                } else {
                    return Future.succeededFuture(new ArrayList<>());
                }
            });
        }
        return futureIt.map(r -> {
            //metrics
            metrics.put("last_fetch_max_attempt", maxAttempt.orElse(0));
            metrics.put("last_fetch_max_batch_size", maxBatchSize);
            metrics.put("last_fetch_at", new Date().getTime());
            metrics.put("last_fetch_size", r.size());
            metrics.put("fetch_count", metrics.getInteger("fetch_count", 0)+1);
            return toMessage(result);
        });
    }

    protected List<MessageIngester.Message> toMessage(final List<JsonObject> result) {
        final List<MessageIngester.Message> messages = new ArrayList<>();
        for (final JsonObject row : result) {
            final String resourceAction = row.getString("resource_action");
            final String idQueue = row.getString(RedisClient.ID_STREAM);
            final String nameStream = row.getString(RedisClient.NAME_STREAM);
            final String idResource = row.getString("id_resource");
            final Integer attemptCount = row.getInteger("attempt_count", 0);
            final JsonObject json = row.getJsonObject("payload");
            final MessageIngester.Message message = new MessageIngester.Message(resourceAction, idQueue, idResource, json);
            message.metadata.put(RedisClient.NAME_STREAM, nameStream);
            message.metadata.put(ATTEMPT_COUNT, attemptCount);
            messages.add(message);
        }
        return messages;
    }

    protected JsonObject toJson(final MessageIngester.Message message) {
        final JsonObject json = new JsonObject();
        json.put("resource_action", message.action);
        json.put("id_resource", message.idResource);
        json.put("payload", message.payload);
        return json;
    }

    @Override
    public Future<List<MessageIngester.Message>> getIncomingMessages(final int maxBatchSize) {
        final List<JsonObject> result = cleanPending();
        if (result.size() >= maxBatchSize) {
            return fetchAllStreams(result, maxBatchSize, Optional.empty(), Optional.empty());
        } else {
            return Future.succeededFuture(toMessage(result));
        }
    }

    @Override
    public Future<List<MessageIngester.Message>> getFailedMessages(final int maxBatchSize, final int maxAttempt) {
        return fetchAllStreams(new ArrayList<>(), maxBatchSize, Optional.of(this.streamFailSuffix), Optional.of(maxAttempt));
    }

    @Override
    public Future<Void> updateStatus(final IngestJob.IngestJobResult ingestResult, final int maxAttempt) {
        //prepare
        final Map<String, List<String>> toDelete = new HashMap<>();
        final Map<String, List<JsonObject>> toFailStreams = new HashMap<>();
        for (final MessageIngester.Message mess : ingestResult.succeed) {
            final String idQueue = mess.idQueue;
            final String stream = mess.metadata.getString(RedisClient.NAME_STREAM);
            toDelete.putIfAbsent(stream, new ArrayList<>());
            toDelete.get(stream).add(idQueue);
        }
        for (final MessageIngester.Message mess : ingestResult.failed) {
            final String idQueue = mess.idQueue;
            final String stream = mess.metadata.getString(RedisClient.NAME_STREAM);
            final Integer attemptCount = mess.metadata.getInteger(ATTEMPT_COUNT);
            final JsonObject json = toJson(mess).put("attempt_count", attemptCount + 1).put("attempted_at", new Date().getTime());
            toDelete.putIfAbsent(stream, new ArrayList<>());
            toDelete.get(stream).add(idQueue);
            toFailStreams.putIfAbsent(stream + streamFailSuffix, new ArrayList<>());
            toFailStreams.get(stream).add(json);
        }
        //push to failed stream
        final List<Future> addFutures = new ArrayList<>();
        for (final String stream : toFailStreams.keySet()) {
            addFutures.add(redisClient.xAdd(stream, toFailStreams.get(stream)));
        }
        return CompositeFuture.all(addFutures).compose(add -> {
            //delete all
            final List<Future> delFutures = new ArrayList<>();
            for (final String stream : toDelete.keySet()) {
                delFutures.add(redisClient.xDel(stream, toDelete.get(stream)));
            }
            return CompositeFuture.all(delFutures);
        }).mapEmpty();
    }

    @Override
    public Future<JsonObject> getMetrics() {
        final JsonObject metrics = this.metrics.copy();
        final List<Future> futures = new ArrayList<>();
        for(final String stream : this.streams){
            futures.add(redisClient.xInfo(stream).onSuccess(info -> {
                metrics.put("stream_"+stream+"_info", info);
            }));
            futures.add(redisClient.xInfo(stream+streamFailSuffix).onSuccess(info -> {
                metrics.put("stream_"+stream+streamFailSuffix+"_info", info);
            }));
        }
        //TODO age of first and last foreach stream and reformat stream infos
        return CompositeFuture.all(futures).map(e -> {
            return metrics;
        });
    }
}
