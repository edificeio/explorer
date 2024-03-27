package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticBulkBuilder;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageIngesterElastic implements MessageIngester {
    static Logger log = LoggerFactory.getLogger(MessageIngesterElastic.class);

    private final ElasticClientManager elasticClient;
    private final IngestJobMetricsRecorder ingestJobMetricsRecorder;
    private final ElasticClient.ElasticOptions elasticOptions;
    public MessageIngesterElastic(final ElasticClientManager elasticClient,
                                  final IngestJobMetricsRecorder ingestJobMetricsRecorder,
                                  final JsonObject config) {
        this.elasticClient = elasticClient;
        this.ingestJobMetricsRecorder = ingestJobMetricsRecorder;
        this.elasticOptions = new ElasticClient.ElasticOptions();
        if(config.containsKey("opensearch-options")) {
            final JsonObject optionsParams = config.getJsonObject("opensearch-options");
            if(optionsParams.containsKey("wait-for")) {
                this.elasticOptions.withWaitFor(optionsParams.getBoolean("wait-for"));
            }
            if(optionsParams.containsKey("refresh")) {
                this.elasticOptions.withRefresh(optionsParams.getBoolean("refresh"));
            }
        }
        log.info("Elasticoptions is " + Json.encode(elasticOptions));
    }

    //TODO if 429 retry and set maxBatchSize less than
    //TODO if payload greater than max reduce maxPayload
    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages) {
        if(messages.isEmpty()){
            return Future.succeededFuture(new IngestJob.IngestJobResult());
        }
        final List<MessageIngesterElasticOperation> operations = new ArrayList<>();
        final List<ExplorerMessageForIngest> failedTransformationToOperation = new ArrayList<>();
        for (ExplorerMessageForIngest message : messages) {
            try {
                final List<MessageIngesterElasticOperation> operation = MessageIngesterElasticOperation.create(message);
                // Exclude move actions from the list of messages to process in OpenSearch, otherwise the resource
                // appears in 2 different folders : the desired one and rootFolder.
                if(!ExplorerMessage.ExplorerAction.Move.name().equals(message.getAction())) {
                    operations.addAll(operation);
                }
            } catch (Exception e) {
                message.setError("to.elastic.operation.failed");
                message.setErrorDetails(e.getMessage());
                failedTransformationToOperation.add(message);
            }
        }
        return executeOperations(operations)
                .map(result -> {
                    // Add Move messages to the list of succeeded messages, otherwise they will be considered as failed
                    // by the ingestion job
                    final List<ExplorerMessageForIngest> moveMessages = messages.stream()
                      .filter(m -> ExplorerMessage.ExplorerAction.Move.name().equals(m.getAction()))
                      .collect(Collectors.toList());
                    result.succeed.addAll(moveMessages);
                    result.failed.addAll(failedTransformationToOperation);
                    return result;
                });
        /**
         //metrics
         this.lastIngestCount = results.size();
         this.nbIngested += results.size();
         this.nbSuccess = succeed.size();
         this.nbFailed = failed.size();
         this.nbIngestion++;
         this.lastIngestion = new Date();
         for (final MessageIngesterElasticOperation op : operations) {
         nbIngestedPerAction.putIfAbsent(op.getMessage().getAction(), 0l);
         nbIngestedPerAction.compute(op.getMessage().getAction(), (key, val) -> val + 1l);
         }
         */
    }

    private Future<IngestJob.IngestJobResult> executeOperations(final List<MessageIngesterElasticOperation> operations) {
        final IngestJob.IngestJobResult ingestJobResult = new IngestJob.IngestJobResult();
        if(operations.isEmpty()) {
            return Future.succeededFuture(ingestJobResult);
        }
        return elasticClient.getClient().bulk(elasticOptions).compose(bulk -> {
            for (MessageIngesterElasticOperation operation : operations) {
                operation.execute(bulk);
            }
            long start = System.currentTimeMillis();
            return bulk.end().map(results -> {
                long delay = System.currentTimeMillis() - start;
                int nbOk = 0;
                int nbKo = 0;
                if (results.isEmpty()) {
                  nbKo = operations.size();
                } else {
                    //categorise
                    final List<ExplorerMessageForIngest> succeed = ingestJobResult.getSucceed();
                    final List<ExplorerMessageForIngest> failed = ingestJobResult.getFailed();
                    //
                    for (int i = 0; i < results.size(); i++) {
                        final ElasticBulkBuilder.ElasticBulkRequestResult res = results.get(i);
                        final MessageIngesterElasticOperation op = operations.get(i);
                        //dont need to ACK subresources
                        if (op instanceof MessageIngesterElasticOperation.MessageIngesterElasticOperationUpsertSubResource) {
                            continue;
                        }
                        if (res.isOk()) {
                            nbOk++;
                            succeed.add(op.message);
                        } else {
                            nbKo++;
                            //if deleted is not found => suceed
                            if ("not_found".equals(res.getMessage())) {
                                succeed.add(op.message);
                                if(!ExplorerMessage.ExplorerAction.Delete.name().equals(op.getMessage().getAction())) {
                                    log.warn("[MessageIngesterElastic] A not_found error was raised for a " + op.getMessage().getAction() + ": " + Json.encode(operations.get(i)));
                                }
                            } else {
                                log.warn("[MessageIngesterElastic] Error in ES for body : " + Json.encode(operations.get(i)));
                                op.message.setError("elastic.ingestion.error: " + res.getMessage());
                                op.message.setErrorDetails(res.getDetails());
                                failed.add(op.message);
                            }
                        }
                    }
                }
                ingestJobMetricsRecorder.onIngestOpenSearchResult(nbOk, nbKo, delay);
                return ingestJobResult;
            }).otherwise(th -> {
                long delay = System.currentTimeMillis() - start;
                final List<ExplorerMessageForIngest> failed = ingestJobResult.getFailed();
                for (int i = 0; i < operations.size(); i++) {
                    final MessageIngesterElasticOperation op = operations.get(i);
                    op.message.setError("elastic.ingestion.error: " + th.getMessage());
                    op.message.setErrorDetails(th.toString());
                    failed.add(op.message);
                }
                ingestJobMetricsRecorder.onIngestOpenSearchResult(0, operations.size(), delay);
                return ingestJobResult;
            });
        });
    }
}
