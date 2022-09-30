package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticBulkBuilder;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.*;
import java.util.stream.Collectors;

abstract class MessageIngesterElasticOperation {
    protected Logger log = LoggerFactory.getLogger(getClass());
    protected final ExplorerMessageForIngest message;

    public ExplorerMessage getMessage() {
        return message;
    }

    public MessageIngesterElasticOperation(final ExplorerMessageForIngest message) {
        this.message = message;
    }

    static  List<MessageIngesterElasticOperation> create(final ExplorerMessageForIngest message) {
        final ExplorerMessage.ExplorerAction a = ExplorerMessage.ExplorerAction.valueOf(message.getAction());
        switch (a) {
            case Delete:
                return Arrays.asList(new MessageIngesterElasticOperationDelete(message));
            case Upsert:
                //prepare
                final List<MessageIngesterElasticOperation> operations = new ArrayList<>();
                operations.add(new MessageIngesterElasticOperationUpsert(message));
                //subresource
                final List<JsonObject> subresources = message.getSubresources().stream().filter(e-> e instanceof JsonObject).map(e-> (JsonObject)e).collect(Collectors.toList());
                if(!subresources.isEmpty()){
                    operations.add(new MessageIngesterElasticOperationUpsertSubResource(message));
                }
                return operations;
            case Audience:
                return Arrays.asList(new MessageIngesterElasticOperationAudience(message));
        }
        return Arrays.asList(new MessageIngesterElasticOperationNoop(message));
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
            //copy for upsert
            final JsonObject insert = MessageIngesterElastic.beforeCreate(copy());
            final JsonObject update = MessageIngesterElastic.beforeUpdate(copy());
            final String id = message.getPredictibleId().orElse(message.getId());
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            final String index = ExplorerConfig.getInstance().getIndex(application, resource);
            bulk.upsert(insert, update, Optional.ofNullable(id), Optional.of(index), Optional.ofNullable(routing));
        }

        private JsonObject copy(){
            final JsonObject original = message.getMessage();
            final JsonObject copy = original.copy();
            copy.remove("subresources");
            return copy;
        }
    }



    static class MessageIngesterElasticOperationUpsertSubResource extends MessageIngesterElasticOperation {
        MessageIngesterElasticOperationUpsertSubResource(final ExplorerMessageForIngest message) {
            super(message);
        }

        @Override
        void execute(final ElasticBulkBuilder bulk) {
            final List<JsonObject> subresources = message.getSubresources().stream().filter(e-> e instanceof JsonObject).map(e-> (JsonObject)e).collect(Collectors.toList());
            if(subresources.isEmpty()){
                return;
            }
            final Set<String> toAddIds = new HashSet<>();
            final Set<String> toDelete = new HashSet<>();
            final List<JsonObject> toAdd = new ArrayList<>();
            for(final JsonObject sub : subresources){
                final Object idObj = sub.getValue("id");
                if(idObj != null){
                    final String id = idObj.toString();
                    final boolean deleted = sub.getBoolean("deleted", false);
                    if(deleted){
                        toDelete.add(id);
                    }else{
                        toAdd.add(sub);
                        toAddIds.add(id);
                    }
                }
            }
            //script source
            final StringBuilder source = new StringBuilder();
            source.append("if(ctx._source.subresources==null) ctx._source.subresources = [];");
            source.append("ctx._source.subresources.removeIf(item -> params.toAddIds.contains(item.id.toString()));");
            source.append("ctx._source.subresources.addAll(params.toAdd);");
            source.append("ctx._source.subresources.removeIf(item -> params.toDelete.contains(item.id.toString()));");
            //params
            final JsonObject params = new JsonObject();
            params.put("toAddIds", new JsonArray(new ArrayList(toAddIds)));
            params.put("toDelete", new JsonArray(new ArrayList(toDelete)));
            params.put("toAdd", new JsonArray(toAdd));
            //meta
            final String application = message.getApplication();
            final String resource = message.getResourceType();
            final String id = message.getPredictibleId().orElse(message.getId());
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            final String index = ExplorerConfig.getInstance().getIndex(application, resource);
            bulk.script(source.toString(), params,Optional.ofNullable(id), Optional.of(index), Optional.ofNullable(routing));
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
