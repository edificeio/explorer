package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticBulkBuilder;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.ArrayList;
import java.util.List;

public class MessageIngesterElastic implements MessageIngester {
    static Logger log = LoggerFactory.getLogger(MessageIngesterElastic.class);

    private final ElasticClientManager elasticClient;
    private final IngestJobMetricsRecorder ingestJobMetricsRecorder;
    public MessageIngesterElastic(final ElasticClientManager elasticClient,
                                  final IngestJobMetricsRecorder ingestJobMetricsRecorder) {
        this.elasticClient = elasticClient;
        this.ingestJobMetricsRecorder = ingestJobMetricsRecorder;
    }

    //TODO if 429 retry and set maxBatchSize less than
    //TODO if payload greater than max reduce maxPayload
    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages) {
        if(messages.isEmpty()){
            return Future.succeededFuture(new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>()));
        }
        final List<MessageIngesterElasticOperation> operations = new ArrayList<>();
        final List<ExplorerMessageForIngest> failedTransformationToOperation = new ArrayList<>();
        for (ExplorerMessageForIngest message : messages) {
            try {
                operations.addAll(MessageIngesterElasticOperation.create(message));
            } catch (Exception e) {
                message.setError("to.elastic.operation.failed");
                message.setErrorDetails(e.getMessage());
                failedTransformationToOperation.add(message);
            }
        }
        return executeOperations(operations)
                .map(result -> {
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
        final IngestJob.IngestJobResult ingestJobResult = new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>());
        final ElasticBulkBuilder bulk = elasticClient.getClient().bulk(new ElasticClient.ElasticOptions().withWaitFor(true));
        for (MessageIngesterElasticOperation operation : operations) {
            operation.execute(bulk);
        }
        long start = System.currentTimeMillis();
        return bulk.end().map(results -> {
            long delay = System.currentTimeMillis() - start;
            int nbOk = 0;
            int nbKo = 0;
            if (results.isEmpty()) {
                nbOk = 0;
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
                        if (ExplorerMessage.ExplorerAction.Delete.name().equals(op.getMessage().getAction()) && "not_found".equals(res.getMessage())) {
                            succeed.add(op.message);
                        } else {
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
    }
}
