package com.opendigitaleducation.explorer.services.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ElasticFolderQuery {
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> parentId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private Integer from;
    private Integer size;

    public ElasticFolderQuery withFrom(Integer from) {
        this.from = from;
        return this;
    }

    public ElasticFolderQuery withSize(Integer size) {
        this.size = size;
        return this;
    }

    public ElasticFolderQuery withCreatorId(final String creatorId) {
        this.creatorId.add(creatorId);
        return this;
    }

    public ElasticFolderQuery withParentId(final String parentId) {
        this.parentId.add(parentId);
        return this;
    }

    public ElasticFolderQuery withId(final String id) {
        this.id.add(id);
        return this;
    }

    public ElasticFolderQuery withId(final List<String> id) {
        this.id.addAll(id);
        return this;
    }

    public List<String> getCreatorId() {
        return creatorId;
    }

    public List<String> getParentId() {
        return parentId;
    }

    public List<String> getId() {
        return id;
    }

    public JsonObject getSearchQuery() {
        final JsonObject payload = new JsonObject();
        final JsonObject query = new JsonObject();
        final JsonObject bool = new JsonObject();
        final JsonArray filter = new JsonArray();
        payload.put("query", query);
        query.put("bool", bool);
        bool.put("filter", filter);
        //by creator
        if (creatorId.size() > 0) {
            Object value = creatorId.size() == 1 ? creatorId.get(0) : new JsonArray(creatorId);
            filter.add(new JsonObject().put("term", new JsonObject().put("creatorId", value)));
        }
        //by parent
        if (parentId.size() > 0) {
            Object value = parentId.size() == 1 ? parentId.get(0) : new JsonArray(parentId);
            filter.add(new JsonObject().put("term", new JsonObject().put("parentId", value)));
        }
        //by id
        if (id.size() > 0) {
            Object value = id.size() == 1 ? id.get(0) : new JsonArray(id);
            filter.add(new JsonObject().put("term", new JsonObject().put("_id", value)));
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
