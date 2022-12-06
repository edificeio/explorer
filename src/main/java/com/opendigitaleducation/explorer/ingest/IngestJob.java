package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ingest.impl.MessageMergerRepository;
import com.opendigitaleducation.explorer.ingest.impl.MessageTransformerChain;
import fr.wseduc.webutils.DefaultAsyncResult;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.postgres.IPostgresClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IngestJob {
    static Logger log = LoggerFactory.getLogger(IngestJob.class);
    public static final String INGESTOR_JOB_ADDRESS = "explorer.ingestjob";
    public static final String INGESTOR_STATUS = "status";
    public static final String INGESTOR_JOB_METRICS = "metrics";
    public static final String INGESTOR_JOB_TRIGGER = "trigger";
    static final int DEFAULT_BATCH_SIZE = 100;
    static final int DEFAULT_MAX_ATTEMPT = 10;
    static final int DEFAULT_MAX_DELAY_MS = 45000;
    private final int batchSize;
    private final int maxAttempt;
    private final Vertx vertx;
    private final MessageReader messageReader;
    private final MessageIngester messageIngester;
    private final int maxDelayBetweenExecutionMs;
    private final List<Future> pending = new ArrayList<>();
    private long idExecution = 0;
    private long nextExecutionTimerId = -1;
    private Function<Void, Void> subscription;
    private IngestJobStatus status = IngestJobStatus.Idle;
    private Handler<AsyncResult<IngestJob.IngestJobResult>> onExecutionEnd = e -> {
    };
    private boolean pendingNotification = false;
    private final MessageConsumer messageConsumer;

    private final MessageTransformerChain messageTransformer;

    private final MessageMerger messageMerger;

    private final IngestJobMetricsRecorder ingestJobMetricsRecorder;

    public IngestJob(final Vertx vertx, final MessageReader messageReader, final MessageIngester messageIngester, final IngestJobMetricsRecorder metricsRecorder, final JsonObject config) {
        this.vertx = vertx;
        this.messageReader = messageReader;
        this.messageIngester = messageIngester;
        this.messageMerger = MessageMergerRepository.getMerger(config.getString("message-merger", "default"));
        this.maxAttempt = config.getInteger("max-attempt", DEFAULT_MAX_ATTEMPT);
        this.batchSize = config.getInteger("batch-size", DEFAULT_BATCH_SIZE);
        this.maxDelayBetweenExecutionMs = config.getInteger("max-delay-ms", DEFAULT_MAX_DELAY_MS);
        this.messageTransformer = new MessageTransformerChain();
        this.ingestJobMetricsRecorder = metricsRecorder;
        this.ingestJobMetricsRecorder.onJobStarted();
        messageConsumer = vertx.eventBus().consumer(INGESTOR_JOB_ADDRESS, message -> {
            final String action = message.headers().get("action");
            switch (action) {
                case INGESTOR_STATUS:
                    final String method = message.headers().get("method");
                    final Future<Void> future = "stop".equalsIgnoreCase(method)? this.stop(): ("start".equalsIgnoreCase(method))?this.start(): Future.succeededFuture();
                    future.onComplete(e->{
                        if (e.succeeded()) {
                            message.reply(new JsonObject().put("running", this.isRunning()));
                            log.info("Ingest job has been "+method);
                        } else {
                            message.fail(500, e.cause().getMessage());
                            log.error("Ingest job failed to "+method, e.cause());
                        }
                    });
                    break;
                case INGESTOR_JOB_TRIGGER:
                    execute(true).onComplete(ee -> {
                        if (ee.succeeded()) {
                            getMetrics().onComplete(e -> {
                                if (e.succeeded()) {
                                    message.reply(e.result());
                                } else {
                                    message.fail(500, e.cause().getMessage());
                                    log.error("Ingest job failed to get metrics for trigger", e.cause());
                                }
                            });
                        } else {
                            message.fail(500, ee.cause().getMessage());
                            log.error("Ingest job failed to trigger", ee.cause());
                        }
                    });
                    break;
                case INGESTOR_JOB_METRICS:
                default:
                    getMetrics().onComplete(e -> {
                        if (e.succeeded()) {
                            message.reply(e.result());
                        } else {
                            message.fail(500, e.cause().getMessage());
                            log.error("Ingest job failed to get metrics", e.cause());
                        }
                    });
                    break;
            }
        });
    }

    public void stopConsumer(){
        this.messageConsumer.unregister();
    }

    public static IngestJob create(final Vertx vertx, final ElasticClientManager manager, final IPostgresClient postgresClient, final JsonObject config, final MessageReader reader) {
        final MessageIngester ingester = MessageIngester.elasticWithPgBackup(manager, postgresClient);
        return new IngestJob(vertx, reader, ingester, IngestJobMetricsRecorderFactory.getIngestJobMetricsRecorder(), config);
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

    public boolean isRunning() {
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
        // lock running
        if (!isRunning() && !force) {
            return Future.failedFuture("resource loader is stopped");
        }
        final List<Future> copyPending = new ArrayList<>(pending);
        final Promise<Void> current = Promise.promise();
        pending.add(current.future());
        idExecution++;
        this.ingestJobMetricsRecorder.onPendingIngestCycleExecutionChanged(pending.size());
        CompositeFuture.all(copyPending).onComplete(onReady -> {
            try {
                this.ingestJobMetricsRecorder.onIngestCycleStarted();
                final long tmpIdExecution = idExecution;
                final Future<List<ExplorerMessageForIngest>> messages = this.messageReader.getMessagesToTreat(batchSize, maxAttempt);
                messages.map(this.messageMerger::mergeMessages)
                .compose(result -> {
                    final List<ExplorerMessageForIngest> messagesToTreat = messageTransformer.transform(result.getMessagesToTreat());
                    return this.messageIngester.ingest(messagesToTreat).map(jobResult -> Pair.of(jobResult, result));
                }).compose(ingestResultAndJobResult -> {
                    final IngestJobResult ingestResult = ingestResultAndJobResult.getLeft();
                    final Future<IngestJobResult> future;
                    this.ingestJobMetricsRecorder.onIngestCycleResult(ingestResultAndJobResult.getLeft(), ingestResultAndJobResult.getRight());
                    if (ingestResult.size() > 0) {
                        future = this.messageReader.updateStatus(transformIngestResult(ingestResult, ingestResultAndJobResult.getRight()), maxAttempt).map(ingestResult);
                    } else {
                        future = Future.succeededFuture(ingestResult);
                    }
                    return future;
                }).onComplete(messageRes -> {
                    try {
                        if (messageRes.succeeded()) {
                            this.ingestJobMetricsRecorder.onIngestCycleSucceeded();
                            this.onExecutionEnd.handle(new DefaultAsyncResult<>(messageRes.result()));
                        } else {
                            this.ingestJobMetricsRecorder.onIngestCycleFailed();
                            log.error("Failed to load messages on search engine:", messageRes.cause());
                            this.onExecutionEnd.handle(new DefaultAsyncResult<>(messageRes.cause()));
                        }
                    } catch (Exception exc) {
                    } finally {
                        this.ingestJobMetricsRecorder.onIngestCycleCompleted();
                        onTaskComplete(current);
                        //if no pending execution => trigger next execution
                        if (tmpIdExecution == idExecution) {
                            scheduleNextExecution(messageRes.otherwise(IngestJobResult.empty()).result());
                        }
                    }
                });
            } catch (Exception e) {
                onTaskComplete(current);
            }
        });
        return current.future();
    }

    private IngestJobResult transformIngestResult(final IngestJobResult ingestResult, final MergeMessagesResult mergedMessages) {
        final Map<String, List<ExplorerMessageForIngest>> messagesByUniqueId = mergedMessages.getMessagesByResourceUniqueId();
        final List<ExplorerMessageForIngest> succeededSourceMessages = ingestResult.succeed.stream()
                .flatMap(message -> messagesByUniqueId.get(message.getResourceUniqueId()).stream())
                .collect(Collectors.toList());
        final List<ExplorerMessageForIngest> failedSourceMessages = ingestResult.failed.stream()
                .flatMap(message -> messagesByUniqueId.get(message.getResourceUniqueId()).stream())
                .collect(Collectors.toList());
        return new IngestJobResult(succeededSourceMessages, failedSourceMessages);
    }

    private void onTaskComplete(final Promise<Void> current) {
        pending.remove(current.future());
        current.complete();
    }

    private void scheduleNextExecution(final IngestJobResult newMessages) {
        vertx.cancelTimer(nextExecutionTimerId);
        if (newMessages.size() >= this.batchSize || newMessages.failed.size() > 0) {
            //messagereader seems to have still some message pending so trigger now
            execute();
        } else {
            //if execute is not trigger before this delay -> execute
            nextExecutionTimerId = vertx.setTimer(maxDelayBetweenExecutionMs, e -> execute());
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

    public Future<Void> waitPending() {
        final List<Future> copyPending = new ArrayList<>(pending);
        return CompositeFuture.all(copyPending).mapEmpty();
    }

    public Future<Void> start() {
        //unlisten previous
        if (subscription != null) {
            subscription.apply(null);
            this.subscription = null;
        }
        //first listen for new message
        subscription = messageReader.listenNewMessages(listen -> {
            //avoid multiple notification at once
            if (pendingNotification) {
                return;
            }
            pendingNotification = true;
            waitPending().onComplete(pending -> {
                pendingNotification = false;
                execute();
            });
        });
        this.status = IngestJobStatus.Running;
        //start reader (listening)
        return this.messageReader.start().compose(ee -> {
            if (pending.isEmpty()) {
                //execute for first time (history)
                final Future<Void> future = execute();
                return future;
            } else {
                //already running
                return waitPending();
            }
        });
    }

    public Future<Void> stop() {
        this.ingestJobMetricsRecorder.onJobStopped();
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
        final List<ExplorerMessageForIngest> succeed;
        final List<ExplorerMessageForIngest> failed;

        public IngestJobResult(final List<ExplorerMessageForIngest> succeed, final List<ExplorerMessageForIngest> failed) {
            this.succeed = succeed;
            this.failed = failed;
        }

        public static IngestJobResult empty() {
            return new IngestJobResult(new ArrayList<>(), new ArrayList<>());
        }

        public List<ExplorerMessageForIngest> getSucceed() {
            return succeed;
        }

        public List<ExplorerMessageForIngest> getFailed() {
            return failed;
        }

        public int size() {
            return succeed.size() + failed.size();
        }
    }

    public MessageTransformerChain getMessageTransformer() {
        return messageTransformer;
    }
}
