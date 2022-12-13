package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.ingest.impl.DeleteElasticOperation;
import com.opendigitaleducation.explorer.ingest.impl.ExplorerScriptedUpsert;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.NotImplementedException;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.*;

public class MessageIngesterElastic implements MessageIngester {
    static Logger log = LoggerFactory.getLogger(MessageIngesterElastic.class);

    private final ElasticClientManager elasticClient;
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
        final List<ExplorerElasticOperation> operations = new ArrayList<>();
        final List<ExplorerMessageForIngest> failedTransformationToOperation = new ArrayList<>();
        for (ExplorerMessageForIngest message : messages) {
            try {
                operations.add(toElasticOperation(message));
            } catch (Exception e) {
                message.setError("to.elastic.operation.failed");
                message.setErrorDetails(e.getMessage());
                failedTransformationToOperation.add(message);
            }
        }
        return chainOperations(operations)
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

    private Future<IngestJob.IngestJobResult> chainOperations(List<ExplorerElasticOperation> operations) {
        final IngestJob.IngestJobResult ingestJobResult = new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>());
        return chainOperations(operations, 0, ingestJobResult);
    }

    private Future<IngestJob.IngestJobResult> chainOperations(List<ExplorerElasticOperation> operations, int idxOperation, IngestJob.IngestJobResult ingestJobResult) {
        if(idxOperation >= operations.size()) {
            return Future.succeededFuture(ingestJobResult);
        }
        final ExplorerElasticOperation op = operations.get(idxOperation);
        Future future;
        final Promise promise = Promise.promise();
        final ElasticClient client = elasticClient.getClient();
        if(op instanceof ExplorerScriptedUpsert) {
            final ExplorerScriptedUpsert scriptedUpsert = (ExplorerScriptedUpsert) op;
            future = client.scriptedUpsert(scriptedUpsert.getIndex(), scriptedUpsert.getId(), scriptedUpsert.toJson(), scriptedUpsert.getOptions());
        } else if(op instanceof DeleteElasticOperation) {
            future = client.deleteDocument(op.getIndex(), op.getId(), op.getOptions());
        } else {
            future = Future.failedFuture("unknown");
        }
        future.onSuccess(id -> ingestJobResult.getSucceed().add(op.getMessage()))
            .onFailure(th -> {
                final ExplorerMessageForIngest message = op.getMessage();
                message.setError(th.toString());
                message.setErrorDetails(th.toString());
                ingestJobResult.getFailed().add(message);
            }).onComplete(futureCompleted -> {
                chainOperations(operations, idxOperation + 1, ingestJobResult).onComplete(e -> {
                    promise.complete(e.result());
                });
            });
        return promise.future();
    }

    private ExplorerElasticOperation toElasticOperation(final ExplorerMessageForIngest message) {
        final ExplorerMessage.ExplorerAction a = ExplorerMessage.ExplorerAction.valueOf(message.getAction());
        switch (a) {
            case Delete:
                return DeleteElasticOperation.create(message);
            case Upsert:
                return ExplorerScriptedUpsert.create(message);
            case Audience:
                // TODO implement
                //return Arrays.asList(new MessageIngesterElasticOperation.MessageIngesterElasticOperationAudience(message));
            default:
                throw new NotImplementedException(a + ".not.implemented");
        }
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
