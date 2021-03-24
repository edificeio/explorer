package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.services.ResourceService;
import fr.wseduc.webutils.DefaultAsyncResult;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class IngestJob {
    static final int DEFAULT_BATCH_SIZE = 100;
    static final int DEFAULT_MAX_ATTEMPT = 10;
    static final int DEFAULT_MAX_DELAY_MS = 45000;
    static Logger log = LoggerFactory.getLogger(IngestJob.class);
    private final int batchSize;
    private final int maxAttempt;
    private final Vertx vertx;
    private final MessageReader messageReader;
    private final MessageIngester messageIngester;
    private final int maxDelayBetweenExecutionMs;
    private long idExecution = 0;
    private long nextExecutionTimerId = -1;
    private Function<Void, Void> subscription;
    private IngestJobStatus status = IngestJobStatus.Idle;
    private List<Future> pending = new ArrayList<>();
    private Handler<AsyncResult<IngestJob.IngestJobResult>> onExecutionEnd = e -> {
    };

    public static IngestJob create(final Vertx vertx, final ResourceService resourceService, final JsonObject config, final MessageReader reader){
        final MessageIngester ingester = new MessageIngesterElastic(resourceService);
        return new IngestJob(vertx, reader, ingester, config);
    }

    public IngestJob(final Vertx vertx, final MessageReader messageReader, final MessageIngester messageIngester, final JsonObject config) {
        this.vertx = vertx;
        this.messageReader = messageReader;
        this.messageIngester = messageIngester;
        this.maxAttempt = config.getInteger("max-attempt", DEFAULT_MAX_ATTEMPT);
        this.batchSize = config.getInteger("batch-size", DEFAULT_BATCH_SIZE);
        this.maxDelayBetweenExecutionMs = config.getInteger("max-delay-ms", DEFAULT_MAX_DELAY_MS);
    }

    public Future<JsonObject> getMetrics() {
        final JsonObject metrics = new JsonObject();
        metrics.put("status", status.name());
        return messageReader.getMetrics().compose(readMetrics -> {
            metrics.put("read", readMetrics);
            return messageIngester.getMetrics();
        }).map(ingestMetrics -> {
            metrics.put("ingest", ingestMetrics);
            return metrics;
        });
    }

    public boolean isRunning(){
        return this.status.equals(IngestJobStatus.Running);
    }

    public Future<Void> execute() {
        return execute(false);
    }

    public Future<Void> execute(boolean force) {
        //TODO dynamic config? (maxattempt, batchsize...)
        //TODO define max concurrent thread
        //TODO define max bulk size according (dynamic bulk size acording to response?)
        //TODO debounce ms? (config)
        //TODO optimize and merge updating related to one resource?
        // lock running
        if (!isRunning() && !force) {
            return Future.failedFuture("resource loader is stopped");
        }
        final List<Future> copyPending = new ArrayList<>(pending);
        final Promise<Void> current = Promise.promise();
        pending.add(current.future());
        idExecution++;
        CompositeFuture.all(copyPending).onComplete(onReady -> {
            try {
                final long tmpIdExecution = idExecution;
                final Future<List<MessageIngester.Message>> newMessage = this.messageReader.getIncomingMessages(batchSize);
                final Future<List<MessageIngester.Message>> failedMessage = this.messageReader.getFailedMessages(batchSize, maxAttempt);
                //load new message
                newMessage.compose(result -> {
                    return this.messageIngester.ingest(result);
                }).compose(ingestResult -> {
                    if(ingestResult.size() > 0){
                        return this.messageReader.updateStatus(ingestResult, maxAttempt).map(ingestResult);
                    }else{
                        return Future.succeededFuture(ingestResult);
                    }
                }).onComplete(newMessageRes -> {
                    //load failed message
                    failedMessage.compose(result -> {
                        return this.messageIngester.ingest(result);
                    }).compose(ingestResult -> {
                        if(ingestResult.size() > 0) {
                            return this.messageReader.updateStatus(ingestResult, maxAttempt).map(ingestResult);
                        }else{
                            return Future.succeededFuture(ingestResult);
                        }
                    }).onComplete(failedMessageRes -> {
                        try {
                            if (newMessageRes.succeeded()) {
                                this.onExecutionEnd.handle(new DefaultAsyncResult<>(newMessageRes.result()));
                            } else {
                                this.onExecutionEnd.handle(new DefaultAsyncResult<>(newMessageRes.cause()));
                                log.error("Failed to load new message on search engine:", newMessageRes.cause());
                            }
                            //
                            if (failedMessageRes.failed()) {
                                log.error("Failed to load retried message on search engine:", failedMessageRes.cause());
                            }
                        } catch (Exception exc) {
                        } finally {
                            onTaskComplete(current);
                            //if no pending execution => trigger next execution
                            if (tmpIdExecution == idExecution) {
                                scheduleNextExecution(newMessageRes.otherwise(IngestJobResult.empty()).result(), failedMessageRes.otherwise(IngestJobResult.empty()).result());
                            }
                        }
                    });
                });
            } catch (Exception e) {
                onTaskComplete(current);
            }
        });
        return current.future();
    }

    private void onTaskComplete(final Promise<Void> current){
        pending.remove(current.future());
        current.complete();
    }

    private void scheduleNextExecution(final IngestJobResult newMessages, final IngestJobResult retryMessages) {
        vertx.cancelTimer(nextExecutionTimerId);
        if (newMessages.size() >= this.batchSize || newMessages.failed.size() > 0 || retryMessages.size() >= this.batchSize || retryMessages.failed.size() > 0) {
            //messagereader seems to have still some message pending so trigger now
            execute();
        } else {
            //if execute is not trigger before this delay -> execute
            nextExecutionTimerId = vertx.setTimer(maxDelayBetweenExecutionMs, e -> {
                execute();
            });
        }
    }

    public Function<Void, Void> onEachExecutionEnd(final Handler<AsyncResult<IngestJob.IngestJobResult>> handler) {
        this.onExecutionEnd = handler;
        //unsubscribe using function
        return e -> {
            this.onExecutionEnd = (ee) -> {
            };
            return null;
        };
    }


    public IngestJobStatus getStatus() {
        return status;
    }

    public Future<Void> waitPending(){
        final List<Future> copyPending = new ArrayList<>(pending);
        return CompositeFuture.all(copyPending).mapEmpty();
    }

    private boolean pendingNotification = false;

    public Future<Void> start() {
        //unlisten previous
        if (subscription != null) {
            subscription.apply(null);
            this.subscription = null;
        }
        //first listen for new message
        subscription = messageReader.listenNewMessages(listen -> {
            //avoid multiple notification at once
            if(pendingNotification){
                return;
            }
            pendingNotification = true;
            waitPending().onComplete(pending->{
                pendingNotification = false;
                execute();
            });
        });
        this.status = IngestJobStatus.Running;
        //start reader (listening)
        return this.messageReader.start().compose(ee->{
            if(pending.isEmpty()){
                //execute for first time (history)
                final Future<Void> future = execute();
                return future;
            }else{
                //already running
                return waitPending();
            }
        });
    }

    public Future<Void> stop() {
        this.messageReader.stop();
        this.status = IngestJobStatus.Stopped;
        if (subscription != null) {
            subscription.apply(null);
            this.subscription = null;
        }
        final Future<Void> future = waitPending().mapEmpty();
        return future.onComplete(ee -> {
            this.onExecutionEnd = e -> {
            };
        });
    }

    public enum IngestJobStatus {
        Idle, Stopped, Running
    }

    public static class IngestJobResult {
        final List<MessageIngester.Message> succeed;
        final List<MessageIngester.Message> failed;

        public IngestJobResult(final List<MessageIngester.Message> succeed, final List<MessageIngester.Message> failed) {
            this.succeed = succeed;
            this.failed = failed;
        }

        public static IngestJobResult empty() {
            return new IngestJobResult(new ArrayList<>(), new ArrayList<>());
        }

        public List<MessageIngester.Message> getSucceed() {
            return succeed;
        }

        public List<MessageIngester.Message> getFailed() {
            return failed;
        }

        public int size() {
            return succeed.size() + failed.size();
        }
    }
}
