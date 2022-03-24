package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticBulkBuilder;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.Optional;

abstract class MessageIngesterElasticOperation {
    protected Logger log = LoggerFactory.getLogger(getClass());
    protected final ExplorerMessageForIngest message;

    public ExplorerMessage getMessage() {
        return message;
    }

    public MessageIngesterElasticOperation(final ExplorerMessageForIngest message) {
        this.message = message;
    }

    static  MessageIngesterElasticOperation create(final ExplorerMessageForIngest message) {
        final ExplorerMessage.ExplorerAction a = ExplorerMessage.ExplorerAction.valueOf(message.getAction());
        switch (a) {
            case Delete:
                return new MessageIngesterElasticOperationDelete(message);
            case Upsert:
                return new MessageIngesterElasticOperationUpsert(message);
            case Audience:
                return new MessageIngesterElasticOperationAudience(message);
        }
        return new MessageIngesterElasticOperationNoop(message);
    }

    abstract void execute(final ElasticBulkBuilder request);


    static class MessageIngesterElasticOperationAudience extends MessageIngesterElasticOperation {
        MessageIngesterElasticOperationAudience(final ExplorerMessageForIngest message) {
            super(message);
        }
        @Override
        void execute(final ElasticBulkBuilder bulk) {
            final String application = message.getApplication();
            final String resource = message.getResourceType();
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            final String id = message.getPredictibleId().orElse(message.getId());
            //TODO implement audience
            final JsonObject audience = message.getMessage().getJsonObject("audience", new JsonObject());
            final String index = ExplorerConfig.getInstance().getIndex(application, resource);
            bulk.update(audience, Optional.ofNullable(id), Optional.of(index), Optional.ofNullable(routing));
        }
    }

    static class MessageIngesterElasticOperationDelete extends MessageIngesterElasticOperation {
        MessageIngesterElasticOperationDelete(final ExplorerMessageForIngest message) {
            super(message);
        }
        @Override
        void execute(final ElasticBulkBuilder bulk) {
            final String application = message.getApplication();
            final String resource = message.getResourceType();
            final String id = message.getPredictibleId().orElse(message.getId());
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            final String index = ExplorerConfig.getInstance().getIndex(application, resource);
            bulk.delete(id, Optional.of(index), Optional.ofNullable(routing));
        }
    }

    static class MessageIngesterElasticOperationUpsert extends MessageIngesterElasticOperation {
        MessageIngesterElasticOperationUpsert(final ExplorerMessageForIngest message) {
            super(message);
        }

        @Override
        void execute(final ElasticBulkBuilder bulk) {
            final String application = message.getApplication();
            final String resource = message.getResourceType();
            //prepare custom fields
            final JsonObject original = message.getMessage();
            //copy for upsert
            final JsonObject insert = MessageIngesterElastic.beforeCreate(original.copy());
            final JsonObject update = MessageIngesterElastic.beforeUpdate(original.copy());
            final String id = message.getPredictibleId().orElse(message.getId());
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            final String index = ExplorerConfig.getInstance().getIndex(application, resource);
            bulk.upsert(insert, update, Optional.ofNullable(id), Optional.of(index), Optional.ofNullable(routing));
        }
    }

    static class MessageIngesterElasticOperationNoop extends MessageIngesterElasticOperation {
        public MessageIngesterElasticOperationNoop(final ExplorerMessageForIngest message) {
            super(message);
        }

        @Override
        void execute(final ElasticBulkBuilder request) {
            log.error("Should not execute noop operation for message:" + message.getId() + "->" + message.getAction());
        }
    }
}
