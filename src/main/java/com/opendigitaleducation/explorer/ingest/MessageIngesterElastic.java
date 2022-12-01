package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticBulkBuilder;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

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
        final List<MessageIngesterElasticOperation> operations = messages.stream().flatMap(mess->
            MessageIngesterElasticOperation.create(mess).stream()
        ).collect(Collectors.toList());
        final ElasticBulkBuilder bulk = elasticClient.getClient().bulk(new ElasticClient.ElasticOptions().withWaitFor(true));
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
                final ElasticBulkBuilder.ElasticBulkRequestResult res = results.get(i);
                final MessageIngesterElasticOperation op = operations.get(i);
                //dont need to ACK subresources
                if(op instanceof MessageIngesterElasticOperation.MessageIngesterElasticOperationUpsertSubResource) {
                    continue;
                }
                if (res.isOk()) {
                    succeed.add(op.message);
                } else {
                    //if deleted is not found => suceed
                    if(ExplorerMessage.ExplorerAction.Delete.name().equals(op.getMessage().getAction()) && "not_found".equals(res.getMessage())){
                        succeed.add(op.message);
                    }else{
                        op.message.setError(res.getMessage());
                        op.message.setErrorDetails(res.getDetails());
                        failed.add(op.message);
                    }
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
        }).otherwise(th -> {
            th.printStackTrace();
            final List<ExplorerMessageForIngest> failed = operations.stream().map(op -> {
                op.message.setError("bulk.operation.error");
                op.message.setErrorDetails(th.getMessage());
                return op.message;
            }).collect(Collectors.toList());
            return new IngestJob.IngestJobResult(
                    emptyList(), failed);
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
        if(!document.containsKey("subresources")){
            document.put("subresources", new JsonArray());
        }
        if (!document.containsKey("trashed")) {
            document.put("trashed", false);
        }
        if (!document.containsKey("public")) {
            document.put("public", false);
        }
        if (!document.containsKey("createdAt")) {
            document.put("createdAt", new Date().getTime());
        }
        if (!document.containsKey("rights")) {
            document.put("rights", new JsonArray());
        }
        if (document.containsKey("creatorId")) {
            final String tagCreator = ExplorerConfig.getCreatorRight(document.getString("creatorId"));
            final JsonArray rights = document.getJsonArray("rights", new JsonArray());
            if(!rights.contains(tagCreator)){
                document.put("rights", rights.add(tagCreator));
            }
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
