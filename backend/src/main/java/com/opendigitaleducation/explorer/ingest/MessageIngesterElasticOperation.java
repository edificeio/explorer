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

import static java.util.Optional.of;
import org.entcore.common.explorer.IExplorerFolderTree;
import org.entcore.common.share.ShareRoles;

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
                if(ExplorerConfig.getInstance().isSkipIndexOfTrashedFolders()
                        && (message.isTrashed() || message.isTrashedBy(message.getUpdaterId()))){
                    //specific indexation for trashed message
                    if(message.isFolderMessage()){
                        //folder are not indexed
                        return Arrays.asList(new MessageIngesterElasticOperationDelete(message));
                    }else{
                        //break folder/resource link
                        message.getMessage().put("folderIds", new JsonArray());
                        message.getMessage().put("usersForFolderIds", new JsonArray());
                        return Arrays.asList(new MessageIngesterElasticOperationUpsert(message));
                    }
                }else{
                    //keep trash flag
                    return Arrays.asList(new MessageIngesterElasticOperationUpsert(message));
                }
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
        private final long version;
        private final JsonObject changes;
        private final int audienceDelta;
        private final JsonArray subresources;
        MessageIngesterElasticOperationUpsert(final ExplorerMessageForIngest message) {
            super(message);
            JsonObject changes = message.getMessage().copy();
            if(message.isSynthetic()) {
                changes = keepOnlyOverride(changes);
            } else if(!message.hasSubResources() && !message.hasRights(true)){
                // we are not updating shares neither subresources => message concern resource
                //TODO decorrelate subresource and resources and add tag to identify creation
                changes = beforeCreate(changes);
            }else {
                // we are updating subresources or shares
                changes = beforeUpdate(changes);
            }
            this.version = message.getVersion();
            this.changes = changes;
            this.audienceDelta = 0;
            this.subresources = message.getSubresources();
        }

        private static JsonObject keepOnlyOverride(JsonObject message) {
            final JsonObject changes = new JsonObject();
            //override field can override existing fields
            final JsonObject override = message.getJsonObject("override", new JsonObject());
            for (final String key : override.fieldNames()) {
                changes.put(key, override.getValue(key));
            }
            changes.put("updatedAt", message.getLong("updatedAt"));
            changes.put("version", message.getLong("version"));
            return changes;
        }

        private static JsonObject beforeCreate(final JsonObject document) {
            document.remove("skipCheckVersion");
            document.remove("entityType");
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
                final String creatorId = document.getString("creatorId");
                final String tagCreator = ShareRoles.getSerializedForCreator(creatorId);
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

        private static JsonObject beforeUpdate(final JsonObject document) {
            // add creator if needed (must be before remove)
            if (document.containsKey("creatorId") && document.containsKey("rights")) {
                final String creatorId = document.getString("creatorId");
                final String tagCreator = ShareRoles.getSerializedForCreator(creatorId);
                final JsonArray rights = document.getJsonArray("rights", new JsonArray());
                if(!rights.contains(tagCreator)){
                    document.put("rights", rights.add(tagCreator));
                }
            }
            document.remove("skipCheckVersion");
            //upsert should remove createdAt
            document.remove("createdAt");
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


        @Override
        void execute(final ElasticBulkBuilder bulk) {
            final String application = message.getApplication();
            final String resource = message.getResourceType();
            final String id = message.getPredictibleId().orElse(message.getId());
            final String routing = ResourceServiceElastic.getRoutingKey(application);
            final String index = ExplorerConfig.getInstance().getIndex(application, resource);
            if(message.getSkipCheckVersion() || IExplorerFolderTree.FOLDER_TYPE.equals(message.getEntityType())){
                //copy for upsert
                final JsonObject insert = beforeCreate(copy());
                final JsonObject update = beforeUpdate(copy());
                bulk.upsert(insert, update, Optional.ofNullable(id), Optional.of(index), Optional.ofNullable(routing));
            }else{
                final JsonObject params = new JsonObject()
                        .put("version", version)
                        .put("changes", changes == null ? new JsonObject() : changes)
                        .put("subresources", subresources == null ? new JsonArray() : subresources)
                        .put("audienceDelta", audienceDelta);
                bulk.storedScript("explorer-upsert-ressource", params, of(id), of(index), of(routing), of(new JsonObject()));
            }
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
