package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.ingest.ExplorerElasticOperation;
import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Date;

public class ExplorerScriptedUpsert implements ExplorerElasticOperation {
    private final long version;
    private final JsonObject changes;
    private final int audienceDelta;
    private final JsonArray subresources;
    private final ExplorerMessageForIngest message;
    private ExplorerScriptedUpsert(long version, JsonObject changes, int audienceDelta, JsonArray subresources,
                                  final ExplorerMessageForIngest message) {
        this.version = version;
        this.changes = changes;
        this.audienceDelta = audienceDelta;
        this.subresources = subresources;
        this.message = message;
    }


    public static ExplorerScriptedUpsert create(final ExplorerMessageForIngest message) {
        JsonObject changes = message.getMessage().copy();
        if(message.isSynthetic()) {
            changes = keepOnlyOverride(changes);
        } else {
            changes = beforeCreate(changes);
        }
        return new ExplorerScriptedUpsert(message.getVersion(), changes, 0, message.getSubresources(), message);
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

    public JsonObject toJson() {
        final JsonObject scriptedUpsert = new JsonObject()
                .put("scripted_upsert", true)
                .put("script", new JsonObject()
                        .put("source", source)
                        .put("lang", "painless")
                        .put("params", new JsonObject()
                                .put("version", version)
                                .put("changes", changes == null ? new JsonObject() : changes)
                                .put("subresources", subresources == null ? new JsonArray() : subresources)
                                .put("audienceDelta", audienceDelta))
                )
                .put("upsert", new JsonObject());
        return scriptedUpsert;
    }

    private static JsonObject beforeCreate(final JsonObject document) {

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

    @Override
    public ExplorerMessageForIngest getMessage() {
        return message;
    }


    private final String source = "if ( ctx.op == 'create' ) {\n" +
            "                ctx._source = params.changes;\n" +
            "                ctx._source.version = params.version;\n" +
            "                if(params.subresources != null) {\n" +
            "                    ctx._source.subresources = params.subresources;\n" +
            "                }\n" +
            "            } else {\n" +
            "                if (ctx._source.version == null || ctx._source.version < params.version) {\n" +
            "                  ctx._source.version = params.version;\n" +
            "                  for(entry in params.changes.entrySet()) {\n" +
            "                    def fieldName = entry.getKey();\n" +
            "                    if (fieldName != 'subresources') {\n" +
            "                        ctx._source[fieldName] = entry.getValue();\n" +
            "                    }\n" +
            "                  }\n" +
            "                }\n" +
            "                if (ctx._source.subresources == null) {\n" +
            "                    ctx._source.subresources = [];\n" +
            "                }\n" +
            "                if(params.subresources != null) {\n" +
            "                    for(subresource in params.subresources) {\n" +
            "                        def srdeleted = subresource.deleted != null && subresource.deleted;\n" +
            "                        def srId = subresource.id;\n" +
            "                        def matchedsr = null;\n" +
            "                        def index = -1;\n" +
            "                        def matchedIndex = null;\n" +
            "                        for(existingSubresource in ctx._source.subresources) {\n" +
            "                            index += 1;\n" +
            "                            if (existingSubresource.id == srId) {\n" +
            "                                matchedsr = existingSubresource;\n" +
            "                                matchedIndex = index;\n" +
            "                                break;\n" +
            "                            }\n" +
            "                        }\n" +
            "                        if(matchedsr == null) {\n" +
            "                            if(!srdeleted) {\n" +
            "                                ctx._source.subresources.add(subresource);\n" +
            "                            }\n" +
            "                        } else if(matchedsr.version < subresource.version) {\n" +
            "                            if(srdeleted) {\n" +
            "                                ctx._source.subresources.remove(matchedIndex);\n" +
            "                            } else {\n" +
            "                                ctx._source.subresources[matchedIndex] = subresource;\n" +
            "                            }\n" +
            "                        }\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n";
}
