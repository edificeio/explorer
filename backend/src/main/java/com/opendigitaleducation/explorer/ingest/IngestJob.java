package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ingest.impl.*;
import fr.wseduc.webutils.DefaultAsyncResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import org.apache.commons.lang3.tuple.Pair;
import org.entcore.common.elasticsearch.ElasticClientManager;
import static org.entcore.common.explorer.IExplorerPlugin.addressForIngestStateUpdate;

import org.entcore.common.explorer.*;
import org.entcore.common.explorer.to.ExplorerReindexResourcesRequest;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
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
    private final int maxBatchSize;
    private int batchSize;
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

    private PermanentIngestionErrorHandler permanentIngestionErrorHandler;

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
        this.maxBatchSize = config.getInteger("batch-size", DEFAULT_BATCH_SIZE);
        this.batchSize = maxBatchSize;
        this.maxDelayBetweenExecutionMs = config.getInteger("max-delay-ms", DEFAULT_MAX_DELAY_MS);
        this.messageTransformer = new MessageTransformerChain();
        this.messageConsumer = getRouter(vertx);
        this.ingestJobMetricsRecorder = metricsRecorder;
        loadTransformerChain(config);
        this.permanentIngestionErrorHandler = createIngestionErrorHandler(vertx, config);
    }

    private MessageConsumer getRouter(Vertx vertx) {
        return vertx.eventBus().consumer(INGESTOR_JOB_ADDRESS, message -> {
            final String action = message.headers().get("action");
            switch (action) {
                case INGESTOR_STATUS:
                    onStatusMessageReceived(message);
                case INGESTOR_JOB_TRIGGER:
                    onJobTriggerMessageReceived(message);
                case INGESTOR_JOB_METRICS:
                default:
                    onJobMetricsMessageReceived(message);
            }
        });
    }

    private void onJobMetricsMessageReceived(Message<Object> message) {
        getMetrics().onComplete(e -> {
            if (e.succeeded()) {
                message.reply(e.result());
            } else {
                message.fail(500, e.cause().getMessage());
                log.error("Ingest job failed to get metrics", e.cause());
            }
        });
    }

    private void onJobTriggerMessageReceived(Message<Object> message) {
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
    }

    private void onStatusMessageReceived(Message<Object> message) {
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
    }

    public void stopConsumer(){
        this.messageConsumer.unregister();
    }

    public static IngestJob create(final Vertx vertx, final ElasticClientManager manager, final IPostgresClient postgresClient, final JsonObject config, final MessageReader reader) {
        final IngestJobMetricsRecorder recorder = IngestJobMetricsRecorderFactory.getIngestJobMetricsRecorder();
        final MessageIngester ingester = MessageIngester.elasticWithPgBackup(manager, postgresClient, recorder, config);
        return new IngestJob(vertx, reader, ingester, recorder, config);
    }

    public static IngestJob createForTest(final Vertx vertx, final ElasticClientManager manager, final IPostgresClient postgresClient, final JsonObject config, final MessageReader reader) {
        config.put("opensearch-options", new JsonObject().put("wait-for", true));
        return create(vertx, manager, postgresClient, config, reader);
    }

    public Future<JsonObject> getMetrics() {
        final JsonObject metrics = new JsonObject();
        metrics.put("status", status.name());
        return Future.succeededFuture(metrics);
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
        final long start = System.currentTimeMillis();
        this.ingestJobMetricsRecorder.onBatchSizeUpdate(this.batchSize);
        final List<Future> copyPending = new ArrayList<>(pending);
        final Promise<Void> current = Promise.promise();
        pending.add(current.future());
        idExecution++;
        this.ingestJobMetricsRecorder.onNewPendingIngestCycle();
        CompositeFuture.all(copyPending).onComplete(onReady -> {
            final long tmpIdExecution = idExecution;
            try {
                this.ingestJobMetricsRecorder.onIngestCycleStarted();
                final Future<List<ExplorerMessageForIngest>> messages = this.messageReader.getMessagesToTreat(batchSize, maxAttempt);

                messages.onSuccess(readMessages -> notifyMessageStateUpdate(readMessages, IngestJobState.RECEIVED))
                .onFailure(current::fail)
                .map(this.messageMerger::mergeMessages)
                .compose(result -> {
                    final List<ExplorerMessageForIngest> messagesToTreat = messageTransformer.transform(result.getMessagesToTreat());
                    log.debug("[IngestResult] [id=" + idExecution + "] Number of message to treat="+messagesToTreat.size()+ " batchSize="+batchSize);
                    return this.messageIngester
                            .ingest(messagesToTreat)
                            .map(jobResult -> Pair.of(jobResult, result))
                            .otherwise(cause -> {
                                // Fail everything
                                for (final ExplorerMessageForIngest failedMessage : messagesToTreat) {
                                    if(isBlank(failedMessage.getError())) {
                                        failedMessage.setError("batch.error");
                                    }
                                    if(isBlank(failedMessage.getErrorDetails())) {
                                        failedMessage.setErrorDetails(cause.toString());
                                    }
                                }
                                final IngestJobResult jobResult = new IngestJobResult(
                                        emptyList(),
                                        messagesToTreat,
                                        emptyList());
                                return Pair.of(jobResult, result);
                            });
                })
                .compose(ingestResultAndJobResult -> {
                    final IngestJobResult ingestResult = ingestResultAndJobResult.getLeft();
                    final Future<IngestJobResult> future;
                    this.ingestJobMetricsRecorder.onIngestCycleResult(ingestResultAndJobResult.getLeft(), ingestResultAndJobResult.getRight(), start);
                    if (ingestResult.size() > 0) {
                        final IngestJobResult transformedJob = transformIngestResult(ingestResult, ingestResultAndJobResult.getRight());
                        ingestResult.succeed.stream().filter(m -> ExplorerMessage.ExplorerAction.Move.name().equals(m.getAction()))
                          .forEach(m -> {
                              final IExplorerPluginClient client = IExplorerPluginClient.withBus(vertx, m.getApplication(), m.getResourceType());
                              client.reindex(new ExplorerReindexResourcesRequest(Collections.singleton(m.getId())));
                          });
                        future = this.messageReader.updateStatus(transformedJob, maxAttempt)
                            .compose(e -> {
                                final List<ExplorerMessageForIngest> permanentlyDeletedMessages =
                                    ingestResult.getFailed().stream()
                                        .filter(m -> m.getAttemptCount() > maxAttempt)
                                        .collect(Collectors.toList());
                                return permanentIngestionErrorHandler.handleDeletedMessages(permanentlyDeletedMessages);
                            })
                            .map(ingestResult);
                    } else {
                        future = Future.succeededFuture(ingestResult);
                    }
                    return future;
                }).onComplete(messageRes -> {
                    try {
                        if (messageRes.succeeded()) {
                            this.ingestJobMetricsRecorder.onIngestCycleSucceeded();
                            final IngestJobResult ingestResult = messageRes.result();
                            log.debug("[IngestResult] [id=" + idExecution + "] Number of message treated="+ingestResult.size()+ " batchSize="+batchSize);
                            notifyMessageStateUpdate(ingestResult.succeed, IngestJobState.OK);
                            notifyMessageStateUpdate(ingestResult.failed, IngestJobState.KO);
                            this.onExecutionEnd.handle(new DefaultAsyncResult<>(messageRes.result()));
                        } else {
                            this.ingestJobMetricsRecorder.onIngestCycleFailed();
                            log.error("Failed to ingest messages:", messageRes.cause());
                            this.onExecutionEnd.handle(new DefaultAsyncResult<>(messageRes.cause()));
                        }
                    } catch (Exception exc) {
                        this.ingestJobMetricsRecorder.onIngestCycleFailed();
                        log.error("Failed to clean up job execution:", exc);
                        this.onExecutionEnd.handle(new DefaultAsyncResult<>(exc));
                    } finally {
                        this.ingestJobMetricsRecorder.onIngestCycleCompleted();
                        this.modifyBatchSizeAfterCycleCompleted(messageRes.result());
                        if(messageRes.succeeded()) {
                            this.logBatchResult(messageRes.result());
                        }
                        onTaskComplete(current);
                        //if no pending execution => trigger next execution
                        if (tmpIdExecution == idExecution) {
                            scheduleNextExecution(messageRes);
                        }
                    }
                });
            } catch (Exception e) {
                onTaskComplete(current);
                //if no other pending execution => trigger next execution
                if (tmpIdExecution == idExecution) {
                    scheduleNextExecution(new DefaultAsyncResult<>(e));
                }
            }
        });
        return current.future();
    }

    private void logBatchResult(final IngestJobResult result) {
        final List<ExplorerMessageForIngest> succeeded = result == null ? emptyList() : result.getSucceed();
        final List<ExplorerMessageForIngest> failed = result == null ? emptyList() : result.getFailed();
        if((succeeded == null || succeeded.isEmpty()) && (failed == null || failed.isEmpty())) {
            log.trace("[IngestResult] [id=" + idExecution + "] Nothing in this batch");
        } else {
            if (failed == null || failed.isEmpty()) {
                log.info("[IngestResult] [id=" + idExecution + "] No error in batch ");
            } else {
                log.warn(failed.size() + " errors in batch " + idExecution);
                for (final ExplorerMessageForIngest explorerMessageForIngest : failed) {
                    if(explorerMessageForIngest.getAttemptCount() > maxAttempt){
                        log.warn("[IngestResult] [id=" + idExecution + "] definitly dropped: " + explorerMessageForIngest);
                    }else{
                        log.warn("[IngestResult] [id=" + idExecution + "] failed: " +
                                " app="+explorerMessageForIngest.getApplication()
                                + " type=" + explorerMessageForIngest.getResourceType()
                                + " entId=" + explorerMessageForIngest.getId()
                                + " queueId=" + explorerMessageForIngest.getIdQueue().orElse("")
                                + " error=" + explorerMessageForIngest.getError()
                                + " detail=" + explorerMessageForIngest.getErrorDetails());
                    }
                }
            }
            if (succeeded == null || succeeded.isEmpty()) {
                log.info("[IngestResult] [id=" + idExecution + "] No successes in batch ");
            } else {
                log.info("[IngestResult] [id=" + idExecution + "] " + succeeded.size() + " successes in batch ");
            }
        }
    }

    private void modifyBatchSizeAfterCycleCompleted(final IngestJobResult result) {
        final List<ExplorerMessageForIngest> succeeded = result == null ? emptyList() : result.getSucceed();
        final List<ExplorerMessageForIngest> failed = result == null ? emptyList() : result.getFailed();
        if(failed == null || failed.isEmpty()) {
            // grow batch size only if some ingest succeed and maxBatchSize is not reached
            if(succeeded.size() > 0 && this.batchSize != maxBatchSize) {
                log.info("Growing back batch size to " + this.batchSize + " after a cycle without failures");
                this.batchSize = Math.min(maxBatchSize, this.batchSize * 2);
                this.ingestJobMetricsRecorder.onBatchSizeUpdate(this.batchSize);
            }
        } else {
            final int newBatchSize = Math.max(1, this.batchSize / 2);
            log.warn("Ingest cycle failed so we are going to shrink the batch size from " + this.batchSize + " to " + newBatchSize);
            this.batchSize = newBatchSize;
            this.ingestJobMetricsRecorder.onBatchSizeUpdate(this.batchSize);
        }
    }

    private static class ApplicationAndEntity {
        private final String application;
        private final String entity;

        private ApplicationAndEntity(final String application, final String entity) {
            this.application = application;
            this.entity = entity;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ApplicationAndEntity)) {
                return false;
            }
            final ApplicationAndEntity that = (ApplicationAndEntity) o;
            return Objects.equals(application, that.application) && Objects.equals(entity, that.entity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(application, entity);
        }
    }

    private void notifyMessageStateUpdate(final List<ExplorerMessageForIngest> readMessages, final IngestJobState state) {
        readMessages.stream()
        .filter(readMessage -> readMessage.getIdQueue().isPresent())
        .collect(Collectors.groupingBy(m -> new ApplicationAndEntity(m.getApplication(), m.getEntityType())))
        .entrySet().stream()
        .forEach(messagesByApplication -> {
            final ApplicationAndEntity key = messagesByApplication.getKey();
            final List<ExplorerMessageForIngest> messages = messagesByApplication.getValue();
            try {
                final List<IngestJobStateUpdateMessage> messagesBatch = messages.stream()
                        .map(readMessage -> new IngestJobStateUpdateMessage(readMessage.getId(), readMessage.getVersion(), state))
                        .collect(Collectors.toList());
                vertx.eventBus().send(addressForIngestStateUpdate(key.application, key.entity), Json.encode(messagesBatch));
            } catch (Exception e) {
                log.error("Could not notify a message state update (" + state + ") : " + messages, e);
            }
        });
    }

    private IngestJobResult transformIngestResult(final IngestJobResult ingestResult, final MergeMessagesResult mergedMessages) {
        final Map<String, List<ExplorerMessageForIngest>> messagesByUniqueId = mergedMessages.getMessagesToAckByTratedMessageIdQueue();
        final List<ExplorerMessageForIngest> succeededSourceMessages = extractMessages(ingestResult.succeed, messagesByUniqueId);
        final List<ExplorerMessageForIngest> failedSourceMessages = extractMessages(ingestResult.failed, messagesByUniqueId);
        final List<ExplorerMessageForIngest> skippedSourceMessages = extractMessages(ingestResult.skipped, messagesByUniqueId);
        return new IngestJobResult(succeededSourceMessages, failedSourceMessages, skippedSourceMessages);
    }

    private List<ExplorerMessageForIngest> extractMessages(final List<ExplorerMessageForIngest> sourceMessagesExtractor,
                                                         final Map<String, List<ExplorerMessageForIngest>> messagesByUniqueId) {
        return sourceMessagesExtractor.stream()
            .filter(message -> message.getIdQueue().isPresent()) // Because some synthetic messages can be added
            .flatMap(message -> messagesByUniqueId.get(message.getIdQueue().get()).stream())
            .collect(Collectors.toList());
    }

    private void onTaskComplete(final Promise<Void> current) {
        pending.remove(current.future());
        if(!current.future().isComplete()){
            current.complete();
        }
    }

    private void scheduleNextExecution(final AsyncResult<IngestJobResult> result) {
        vertx.cancelTimer(nextExecutionTimerId);
        final IngestJobResult messages = result.otherwise(new IngestJobResult()).result();
        if (result.failed() || messages.size() >= this.batchSize || !messages.failed.isEmpty()) {
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
        this.ingestJobMetricsRecorder.onJobStarted();
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
        final List<ExplorerMessageForIngest> skipped;

        public IngestJobResult() {
            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        public IngestJobResult(final List<ExplorerMessageForIngest> succeed,
                               final List<ExplorerMessageForIngest> failed,
                               final List<ExplorerMessageForIngest> skipped) {
            this.succeed = succeed;
            this.failed = failed;
            this.skipped = skipped;
        }

        public List<ExplorerMessageForIngest> getSucceed() {
            return succeed;
        }

        public List<ExplorerMessageForIngest> getFailed() {
            return failed;
        }

        public List<ExplorerMessageForIngest> getSkipped() {
            return skipped;
        }

        public int size() {
            return succeed.size() + failed.size() + skipped.size();
        }
    }

    public MessageTransformerChain getMessageTransformer() {
        return messageTransformer;
    }

    private void loadTransformerChain(final JsonObject config) {
        final JsonArray messageTransformerConfigurations = config.getJsonArray("messageTransformers");
        if(messageTransformerConfigurations == null) {
            this.messageTransformer.clearChain();
        } else {
            final MessageTransformer[] transformers = new MessageTransformer[messageTransformerConfigurations.size()];
            for(int i = 0; i < messageTransformerConfigurations.size(); i++) {
                transformers[i] = MessageTransformerFactory.create(messageTransformerConfigurations.getJsonObject(i));
            }
            this.messageTransformer.clearChain();
            messageTransformer.withTransformer(transformers);
        }
    }

    private PermanentIngestionErrorHandler createIngestionErrorHandler(final Vertx vertx, final JsonObject config) {
        return new ChainPermanentIngestionErrorHandler(
          new MetricsUpdaterPermanentIngestionErrorHandler(ingestJobMetricsRecorder),
          new DebouncedIngestionErrorReplayer(
              vertx,
              config.getLong("reindex-error-debounce-delay-ms", 60000L),
              config.getInteger("reindex-error-debounce-queue-max-size", -1)
          )
        );
    }
}
