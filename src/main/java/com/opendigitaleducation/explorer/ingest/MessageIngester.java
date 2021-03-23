package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.services.ResourceService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface MessageIngester {

    static MessageIngester elastic(final ResourceService resourceService) {
        return new MessageIngesterElastic(resourceService);
    }

    Future<IngestJob.IngestJobResult> ingest(final List<Message> messages);

    Future<JsonObject> getMetrics();

    class Message {
        final String action;
        final String idQueue;
        final String idResource;
        final JsonObject payload;
        final JsonObject metadata = new JsonObject();
        final JsonObject result = new JsonObject();

        public Message(String action, String idQueue, String idResource, JsonObject payload) {
            this.action = action;
            this.idQueue = idQueue;
            this.idResource = idResource;
            this.payload = payload;
        }
    }
}
