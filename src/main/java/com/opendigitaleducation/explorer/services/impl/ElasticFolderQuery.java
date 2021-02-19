package com.opendigitaleducation.explorer.services.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ElasticFolderQuery {
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> parentId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private Integer from;
    private Integer size;

    static <T> Optional<JsonObject> createTerm(final String key, final List<T> values) {
        if (values.size() == 0) {
            return Optional.empty();
        } else if (values.size() == 1) {
            return Optional.of(new JsonObject().put("term", new JsonObject().put(key, values.iterator().next())));
        } else {
            return Optional.of(new JsonObject().put("terms", new JsonObject().put(key, new JsonArray(values))));
        }
    }

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
        final Optional<JsonObject> creatorTerm = createTerm("creatorId", creatorId);
        if (creatorTerm.isPresent()) {
            filter.add(creatorTerm.get());
        }
        //by parent
        final Optional<JsonObject> parentIdTerm = createTerm("parentId", parentId);
        if (parentIdTerm.isPresent()) {
            filter.add(parentIdTerm.get());
        }
        //by id
        final Optional<JsonObject> idTerm = createTerm("_id", id);
        if (idTerm.isPresent()) {
            filter.add(idTerm.get());
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
