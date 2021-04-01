package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.services.ResourceService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

public interface MessageIngester {

    static MessageIngester elastic(final ElasticClientManager elasticClient) {
        return new MessageIngesterElastic(elasticClient);
    }

    Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageDetails> messages);

    Future<JsonObject> getMetrics();

    class ExplorerMessageDetails extends ExplorerMessage{
        private final String idQueue;
        private String error="";
        private String errorDetails="";
        private final JsonObject metadata = new JsonObject();
        public ExplorerMessageDetails(final String resourceAction, final String idQueue, final String idResource, final JsonObject json) {
            super(idResource, ExplorerAction.valueOf(resourceAction), false);
            this.getMessage().mergeIn(json);
            this.idQueue = idQueue;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getErrorDetails() {
            return errorDetails;
        }

        public void setErrorDetails(String errorDetails) {
            this.errorDetails = errorDetails;
        }

        public String getError() {
            return error;
        }

        public String getIdQueue() {
            return idQueue;
        }

        public JsonObject getMetadata() {
            return metadata;
        }
    }
}
