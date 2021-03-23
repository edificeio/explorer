package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.services.ResourceService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

public class MessageIngesterElastic implements MessageIngester {
    static Logger log = LoggerFactory.getLogger(MessageIngesterElastic.class);

    private final ResourceService resourceService;
    private final Map<String, Long> nbIngestedPerAction = new HashMap<>();
    private Date lastIngestion;
    private long nbIngestion = 0;
    private long nbIngested = 0;
    private long nbSuccess = 0;
    private long nbFailed = 0;
    private long lastIngestCount = 0;

    public MessageIngesterElastic(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    //TODO if 429 retry and set maxBatchSize less than
    //TODO if payload greater than max reduce maxPayload
    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<Message> messages) {
        final Map<String, Message> messageByIdQueue = new HashMap<>();
        final List<ResourceService.ResourceBulkOperation<String>> resources = new ArrayList<>();
        for (final Message message : messages) {
            final String resourceAction = message.action;
            final String idQueue = message.idQueue;
            final String idResource = message.idResource;
            final JsonObject payload = message.payload;
            messageByIdQueue.put(idQueue, message);
            //final String creatorId = payload.getString("creatorId");
            payload.put("_id", idResource);
            final ResourceService.ResourceBulkOperationType type = ResourceService.getOperationType(resourceAction);
            resources.add(new ResourceService.ResourceBulkOperation(payload, type, idQueue));
        }
        return this.resourceService.bulkOperations(resources).map((List<JsonObject> bulkResults) -> {
            if (bulkResults.isEmpty()) {
                return new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>());
            }
            //categorise
            final List<Message> succeed = new ArrayList<>();
            final List<Message> failed = new ArrayList<>();
            for (final JsonObject res : bulkResults) {
                final String idQueue = res.getString(ResourceService.CUSTOM_IDENTIFIER);
                final Message original = messageByIdQueue.get(idQueue);
                if(original != null) {
                    final boolean success = res.getBoolean(ResourceService.SUCCESS_FIELD, false);
                    original.result.mergeIn(res);
                    if (success) {
                        succeed.add(original);
                    } else {
                        failed.add(original);
                    }
                }else{
                    log.warn(String.format("Original message not found for idQueue=%s and idResource=%s", original.idQueue, original.idResource));
                }
            }
            //metrics
            this.lastIngestCount = bulkResults.size();
            this.nbIngested += bulkResults.size();
            this.nbSuccess = succeed.size();
            this.nbFailed = failed.size();
            this.nbIngestion++;
            this.lastIngestion = new Date();
            for (final ResourceService.ResourceBulkOperation<String> op : resources) {
                nbIngestedPerAction.putIfAbsent(op.getType().name(), 0l);
                nbIngestedPerAction.compute(op.getType().name(), (key, val) -> val + 1l);
            }
            //return
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
}
