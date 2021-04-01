package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.elastic.ElasticBulkRequest;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class MessageIngesterElastic implements MessageIngester {
    public static final String DEFAULT_RESOURCE_INDEX = "explorer_resource";
    static Logger log = LoggerFactory.getLogger(MessageIngesterElastic.class);

    private final JsonObject esIndexes;
    private final ElasticClientManager elasticClient;
    private final Map<String, Long> nbIngestedPerAction = new HashMap<>();
    private Date lastIngestion;
    private long nbIngestion = 0;
    private long nbIngested = 0;
    private long nbSuccess = 0;
    private long nbFailed = 0;
    private long lastIngestCount = 0;

    public MessageIngesterElastic(final ElasticClientManager elasticClient) {
        this(elasticClient, new JsonObject());
    }

    public MessageIngesterElastic(final ElasticClientManager elasticClient, final JsonObject esIndexes) {
        this.elasticClient = elasticClient;
        this.esIndexes = esIndexes;
    }

    //TODO if 429 retry and set maxBatchSize less than
    //TODO if payload greater than max reduce maxPayload
    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageDetails> messages) {
        final List<MessageIngesterElasticOperation> operations = messages.stream().map(mess->{
            return MessageIngesterElasticOperation.create(mess.getIdQueue(), mess).setEsIndexes(esIndexes);
        }).collect(Collectors.toList());
        //TODO upsert or get id for resources (non folders)
        final ElasticBulkRequest bulk = elasticClient.getClient().bulk(new ElasticClient.ElasticOptions());
        for(final MessageIngesterElasticOperation op : operations){
            op.execute(bulk);
        }
        return bulk.end().map(results -> {
            if (results.isEmpty()) {
                return new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>());
            }
            //categorise
            final List<ExplorerMessageDetails> succeed = new ArrayList<>();
            final List<ExplorerMessageDetails> failed = new ArrayList<>();
            //
            for (int i = 0; i < results.size(); i++) {
                final ElasticBulkRequest.ElasticBulkRequestResult res = results.get(i);
                final MessageIngesterElasticOperation op = operations.get(i);
                if (res.isOk()) {
                    succeed.add(op.message);
                } else {
                    op.message.setError(res.getMessage());
                    op.message.setErrorDetails(res.getDetails());
                    failed.add(op.message);
                }
            }
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
            return new IngestJob.IngestJobResult(succeed, failed);
        });
    }

    @Override
    public Future<JsonObject> getMetrics() {
        final JsonObject metrics = new JsonObject();
        if (lastIngestion != null) {
            metrics.put("last_ingestion_date", this.lastIngestion.toString());
        }
        metrics.put("last_ingest_count", this.lastIngestCount);
        metrics.put("count_failed", this.nbFailed);
        metrics.put("count_success", this.nbSuccess);
        metrics.put("count_ingested", this.nbIngested);
        metrics.put("count_ingestion", this.nbIngestion);
        for (final String key : nbIngestedPerAction.keySet()) {
            metrics.put("count_" + key, nbIngestedPerAction.getOrDefault(key, 0l));
        }
        return Future.succeededFuture(metrics);
    }

    public static String getDefaultIndexName(final String application){
        return DEFAULT_RESOURCE_INDEX+"_"+application;
    }
}
