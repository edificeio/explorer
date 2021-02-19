package com.opendigitaleducation.explorer.services.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.List;

public class ElasticResourceQuery {
    private final UserInfos user;
    private final List<String> application = new ArrayList<>();
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> folderId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private Integer from;
    private Integer size;
    private boolean onlyRoot = false;
    private Boolean trashed;
    private String text;

    public ElasticResourceQuery(final UserInfos u){
        this.user = u;
    }

    public ElasticResourceQuery withOnlyRoot(boolean onlyRoot) {
        this.onlyRoot = onlyRoot;
        return this;
    }

    public ElasticResourceQuery withTextSearch(String text) {
        this.text = text;
        return this;
    }

    public ElasticResourceQuery withApplication(String application) {
        this.application.add(application);
        return this;
    }

    public ElasticResourceQuery withFrom(Integer from) {
        this.from = from;
        return this;
    }

    public ElasticResourceQuery withSize(Integer size) {
        this.size = size;
        return this;
    }

    public ElasticResourceQuery withCreatorId(final String creatorId) {
        this.creatorId.add(creatorId);
        return this;
    }

    public ElasticResourceQuery withFolderId(final String folderId) {
        this.folderId.add(folderId);
        return this;
    }

    public ElasticResourceQuery withId(final String id) {
        this.id.add(id);
        return this;
    }

    public ElasticResourceQuery withId(final List<String> id) {
        this.id.addAll(id);
        return this;
    }

    public List<String> getCreatorId() {
        return creatorId;
    }

    public List<String> getFolderId() {
        return folderId;
    }

    public List<String> getId() {
        return id;
    }

    public JsonObject getSearchQuery() {
        final JsonObject payload = new JsonObject();
        final JsonObject query = new JsonObject();
        final JsonObject bool = new JsonObject();
        final JsonArray filter = new JsonArray();
        final JsonArray mustNot = new JsonArray();
        final JsonArray must = new JsonArray();
        payload.put("query", query);
        query.put("bool", bool);
        bool.put("filter", filter);
        bool.put("must_not", mustNot);
        bool.put("must", must);
        //by creator
        if (creatorId.size() > 0) {
            Object value = creatorId.size() == 1 ? creatorId.get(0) : new JsonArray(creatorId);
            filter.add(new JsonObject().put("term", new JsonObject().put("creatorId", value)));
        }
        //by folder
        if(onlyRoot){
            filter.add(new JsonObject().put("term", new JsonObject().put("visibleBy", ElasticResourceService.getVisibleByCreator(user.getUserId()))));
            mustNot.add(new JsonObject().put("term", new JsonObject().put("usersForFolderIds", user.getUserId())));
        }else if (folderId.size() > 0) {
            Object value = folderId.size() == 1 ? folderId.get(0) : new JsonArray(folderId);
            filter.add(new JsonObject().put("term", new JsonObject().put("folderIds", value)));
        }
        //by id
        if (id.size() > 0) {
            Object value = id.size() == 1 ? id.get(0) : new JsonArray(id);
            filter.add(new JsonObject().put("term", new JsonObject().put("_id", value)));
        }
        //application
        if(application.size() > 0){
            Object value = application.size() == 1 ? application.get(0) : new JsonArray(application);
            filter.add(new JsonObject().put("term", new JsonObject().put("application", value)));
        }
        //search text
        if(text != null){
            final JsonArray fields = new JsonArray().add("application").add("name").add("content");
            must.add(new JsonObject().put("multi_match", new JsonObject().put("query", text).put("fields", fields)));
        }
        //from / size
        if (from != null) {
            payload.put("from", from);
        }
        if (size != null) {
            payload.put("size", size);
        }
        return payload;
    }
}
