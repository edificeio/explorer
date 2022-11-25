package com.opendigitaleducation.explorer.ingest;

import fr.wseduc.webutils.DefaultAsyncResult;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.postgres.IPostgresClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class IngestJob {
    public static final String INGESTOR_JOB_ADDRESS = "explorer.ingestjob";
    public static final String INGESTOR_STATUS = "status";
    public static final String INGESTOR_JOB_METRICS = "metrics";
    public static final String INGESTOR_JOB_TRIGGER = "trigger";
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
    private final List<Future> pending = new ArrayList<>();
    private long idExecution = 0;
    private long nextExecutionTimerId = -1;
    private Function<Void, Void> subscription;
    private IngestJobStatus status = IngestJobStatus.Idle;
    private Handler<AsyncResult<IngestJob.IngestJobResult>> onExecutionEnd = e -> {
    };
    private boolean pendingNotification = false;
    private final MessageConsumer messageConsumer;

    private final boolean allowErrorRulesToBeApplied;

    private List<IngestJobErrorRule> errorRules;

    public IngestJob(final Vertx vertx, final MessageReader messageReader, final MessageIngester messageIngester, final JsonObject config) {
        this.vertx = vertx;
        this.messageReader = messageReader;
        this.messageIngester = messageIngester;
        this.maxAttempt = config.getInteger("max-attempt", DEFAULT_MAX_ATTEMPT);
        this.batchSize = config.getInteger("batch-size", DEFAULT_BATCH_SIZE);
        this.maxDelayBetweenExecutionMs = config.getInteger("max-delay-ms", DEFAULT_MAX_DELAY_MS);
        this.allowErrorRulesToBeApplied = config.getBoolean("error-rules-allowed", false);
        if(allowErrorRulesToBeApplied) {
            errorRules = new ArrayList<>();
        } else {
            errorRules = emptyList();
        }
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
        return new IngestJob(vertx, reader, ingester, config);
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
                final Future<List<ExplorerMessageForIngest>> messages = this.messageReader.getMessagesToTreat(batchSize, maxAttempt);
                messages.compose(result -> {
                    final List<ExplorerMessageForIngest> messagesToTreat;
                    if(allowErrorRulesToBeApplied) {
                        messagesToTreat = transformMessagesBasedOnErrorRules(result);
                    } else {
                        messagesToTreat = result;
                    }
                    return this.messageIngester.ingest(messagesToTreat);
                }).compose(ingestResult -> {
                    if (ingestResult.size() > 0) {
                        return this.messageReader.updateStatus(ingestResult, maxAttempt).map(ingestResult);
                    } else {
                        return Future.succeededFuture(ingestResult);
                    }
                }).onComplete(messageRes -> {
                    try {
                        if (messageRes.succeeded()) {
                            this.onExecutionEnd.handle(new DefaultAsyncResult<>(messageRes.result()));
                        } else {
                            log.error("Failed to load messages on search engine:", messageRes.cause());
                            this.onExecutionEnd.handle(new DefaultAsyncResult<>(messageRes.cause()));
                        }
                    } catch (Exception exc) {
                    } finally {
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

    private List<ExplorerMessageForIngest> transformMessagesBasedOnErrorRules(List<ExplorerMessageForIngest> result) {
        return result.stream().map(message -> {
            final ExplorerMessageForIngest transformedMessage;
            if(messageMatchesError(message)) {
                transformedMessage = transormMessageToGenerateError(message);
            } else {
                transformedMessage = message;
            }
            return transformedMessage;
        }).collect(Collectors.toList());
    }

    /**
     * Generates a message that will generate an error by setting values with wrong types.
     * @param message Message that should generate an error
     * @return A transformed version of the message that will generate an error upon ingestion
     */
    private ExplorerMessageForIngest transormMessageToGenerateError(ExplorerMessageForIngest message) {
        final JsonObject duplicate = message.getMessage().copy();
        final ExplorerMessageForIngest ingest = new ExplorerMessageForIngest(
                message.getAction(),
                message.getIdQueue().orElse(null),
                message.getId(),
                duplicate);
        ingest.getMessage().put("public", 4); // raise an error because we specified "public" as being a boolean in the mapping
        return ingest;
    }

    private boolean messageMatchesError(ExplorerMessageForIngest message) {
        return this.errorRules.stream().anyMatch(errorRule -> {
            if(errorRule.getValuesToTarget() != null) {
                final JsonObject messageBody = message.getMessage();
                final boolean bodyMatch = errorRule.getValuesToTarget().entrySet().stream().allMatch(fieldNameAndValue ->
                    messageBody.getString(fieldNameAndValue.getKey(), "").matches(fieldNameAndValue.getValue())
                );
                if(bodyMatch) {
                    log.debug("Evicting message " + messageBody + " based on " + errorRule);
                } else {
                    return false;
                }
            }
            if(errorRule.getAction() != null && !message.getAction().matches(errorRule.getAction())) {
                return false;
            }
            if(errorRule.getPriority() != null && !message.getPriority().name().matches(errorRule.getPriority())) {
                return false;
            }
            return true;
        });
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

    public void setErrorRules(final List<IngestJobErrorRule> errorRules) {
        if(this.allowErrorRulesToBeApplied) {
            this.errorRules = errorRules;
        }
    }
}
