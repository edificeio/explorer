package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.elastic.ElasticBulkRequest;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class MessageIngesterElastic implements MessageIngester {
    static Logger log = LoggerFactory.getLogger(MessageIngesterElastic.class);

    private final ElasticClientManager elasticClient;
    private final Map<String, Long> nbIngestedPerAction = new HashMap<>();
    private Date lastIngestion;
    private long nbIngestion = 0;
    private long nbIngested = 0;
    private long nbSuccess = 0;
    private long nbFailed = 0;
    private long lastIngestCount = 0;

    public MessageIngesterElastic(final ElasticClientManager elasticClient) {
        this.elasticClient = elasticClient;
    }

    //TODO if 429 retry and set maxBatchSize less than
    //TODO if payload greater than max reduce maxPayload
    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages) {
        if(messages.isEmpty()){
            return Future.succeededFuture(new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>()));
        }
        final List<MessageIngesterElasticOperation> operations = messages.stream().map(mess->{
            return MessageIngesterElasticOperation.create(mess);
        }).collect(Collectors.toList());
        final ElasticBulkRequest bulk = elasticClient.getClient().bulk(new ElasticClient.ElasticOptions().withWaitFor(true));
        for(final MessageIngesterElasticOperation op : operations){
            op.execute(bulk);
        }
        return bulk.end().map(results -> {
            if (results.isEmpty()) {
                return new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>());
            }
            //categorise
            final List<ExplorerMessageForIngest> succeed = new ArrayList<>();
            final List<ExplorerMessageForIngest> failed = new ArrayList<>();
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

    static JsonObject beforeCreate(final JsonObject document) {
        if (!document.containsKey("trashed")) {
            document.put("trashed", false);
        }
        if (!document.containsKey("public")) {
            document.put("public", false);
        }
        if (!document.containsKey("createdAt")) {
            document.put("createdAt", new Date().getTime());
        }
        if (!document.containsKey("visibleBy")) {
            document.put("visibleBy", new JsonArray());
        }
        if (document.containsKey("creatorId")) {
            final String tagCreator = ExplorerConfig.getVisibleByCreator(document.getString("creatorId"));
            document.put("visibleBy", new JsonArray().add(tagCreator));
        }
        if (!document.containsKey("folderIds")) {
            document.put("folderIds", new JsonArray());
        }
        if (!document.containsKey("usersForFolderIds")) {
            document.put("usersForFolderIds", new JsonArray());
        }
        //custom field should not override existing fields
        final JsonObject custom = document.getJsonObject("custom", new JsonObject());
        for (final String key : custom.fieldNames()) {
            if (!document.containsKey(key)) {
                document.put(key, custom.getValue(key));
            }
        }
        document.remove("custom");
        //override field can override existing fields
        final JsonObject override = document.getJsonObject("override", new JsonObject());
        for (final String key : override.fieldNames()) {
            document.put(key, override.getValue(key));
        }
        document.remove("override");
        return document;
    }

    static JsonObject beforeUpdate(final JsonObject document) {
        //upsert should remove createdAt
        document.remove("createdAt");
        document.remove("creatorId");
        document.remove("creatorName");
        document.put("updatedAt", new Date().getTime());
        //custom field should not override existing fields
        final JsonObject custom = document.getJsonObject("custom", new JsonObject());
        for (final String key : custom.fieldNames()) {
            if (!document.containsKey(key)) {
                document.put(key, custom.getValue(key));
            }
        }
        document.remove("custom");
        //override field can override existing fields
        final JsonObject override = document.getJsonObject("override", new JsonObject());
        for (final String key : override.fieldNames()) {
            document.put(key, override.getValue(key));
        }
        document.remove("override");
        return document;
    }

}
