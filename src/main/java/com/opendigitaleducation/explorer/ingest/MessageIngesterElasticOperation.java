package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.elastic.ElasticBulkRequest;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

abstract class MessageIngesterElasticOperation {
    protected Logger log = LoggerFactory.getLogger(getClass());
    protected final MessageIngester.ExplorerMessageDetails message;
    protected final String idQueue;
    protected JsonObject esIndexes = new JsonObject();

    public ExplorerMessage getMessage() {
        return message;
    }

    public MessageIngesterElasticOperation(final MessageIngester.ExplorerMessageDetails message) {
        this.message = message;
        this.idQueue = message.getIdQueue();
    }

    public MessageIngesterElasticOperation setEsIndexes(JsonObject esIndexes) {
        this.esIndexes = esIndexes;
        return this;
    }

    protected String getIndex(final String application){
        //TODO one index per application?
        final String key = MessageIngesterElastic.getDefaultIndexName(application);
        return esIndexes.getString(application, key);
    }

    static  MessageIngesterElasticOperation create(final String idQueue, final MessageIngester.ExplorerMessageDetails message) {
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

    abstract void execute(final ElasticBulkRequest request);


    static class MessageIngesterElasticOperationAudience extends MessageIngesterElasticOperation {
        MessageIngesterElasticOperationAudience(final MessageIngester.ExplorerMessageDetails message) {
            super(message);
        }
        @Override
        void execute(final ElasticBulkRequest bulk) {
            final String application = message.getApplication();
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            //TODO create id predictible
            final String id = message.getId();
            //TODO implement audience
            final JsonObject audience = message.getMessage().getJsonObject("audience", new JsonObject());
            bulk.update(audience, Optional.ofNullable(id), Optional.of(getIndex(application)), Optional.ofNullable(routing));
        }
    }

    static class MessageIngesterElasticOperationDelete extends MessageIngesterElasticOperation {
        MessageIngesterElasticOperationDelete(final MessageIngester.ExplorerMessageDetails message) {
            super(message);
        }
        @Override
        void execute(final ElasticBulkRequest bulk) {
            final String application = message.getApplication();
            //TODO create id predictible
            final String id = message.getId();
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            bulk.delete(id, Optional.of(getIndex(application)), Optional.ofNullable(routing));
        }
    }

    static class MessageIngesterElasticOperationUpsert extends MessageIngesterElasticOperation {
        MessageIngesterElasticOperationUpsert(final MessageIngester.ExplorerMessageDetails message) {
            super(message);
        }

        @Override
        void execute(final ElasticBulkRequest bulk) {
            final String application = message.getApplication();
            //prepare custom fields
            final JsonObject original = message.getMessage();
            //copy for upsert
            final JsonObject doc = ResourceServiceElastic.beforeCreate(original.copy());
            final JsonObject upsert = ResourceServiceElastic.beforeUpdate(original.copy());
            //TODO create id predictible
            final String id = message.getId();
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            bulk.upsert(doc, upsert, Optional.ofNullable(id), Optional.of(getIndex(application)), Optional.ofNullable(routing));
        }
    }

    static class MessageIngesterElasticOperationNoop extends MessageIngesterElasticOperation {
        public MessageIngesterElasticOperationNoop(final MessageIngester.ExplorerMessageDetails message) {
            super(message);
        }

        @Override
        void execute(final ElasticBulkRequest request) {
            log.error("Should not execute noop operation for message:" + message.getId() + "->" + message.getAction());
        }
    }
}
