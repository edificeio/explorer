package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConstants;
import com.opendigitaleducation.explorer.services.FolderService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FolderQueryElastic {
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> parentId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private final List<String> ancestors = new ArrayList<>();
    private Integer from;
    private Integer size;
    private Boolean trashed;

    public FolderQueryElastic withOnlyRoot(boolean onlyRoot) {
        this.parentId.clear();
        this.parentId.add(ExplorerConstants.ROOT_FOLDER_ID);
        return this;
    }

    public FolderQueryElastic withTrashed(Boolean trashed) {
        this.trashed = trashed;
        return this;
    }

    public FolderQueryElastic withFolderId(final String folderId) {
        this.parentId.add(folderId);
        return this;
    }
    //TODO force creator on constructor (every query is by creator)
    static <T> Optional<JsonObject> createTerm(final String key, final List<T> values) {
        if (values.size() == 0) {
            return Optional.empty();
        } else if (values.size() == 1) {
            return Optional.of(new JsonObject().put("term", new JsonObject().put(key, values.iterator().next())));
        } else {
            return Optional.of(new JsonObject().put("terms", new JsonObject().put(key, new JsonArray(values))));
        }
    }

    public FolderQueryElastic withFrom(Integer from) {
        this.from = from;
        return this;
    }

    public FolderQueryElastic withSize(Integer size) {
        this.size = size;
        return this;
    }

    public FolderQueryElastic withCreatorId(final String creatorId) {
        this.creatorId.add(creatorId);
        return this;
    }

    public FolderQueryElastic withParentId(final String parentId) {
        this.parentId.add(parentId);
        return this;
    }

    public FolderQueryElastic withId(final String id) {
        if (id != null) {
            this.id.add(id);
        }
        return this;
    }

    public FolderQueryElastic withAncestors(final String ancestors) {
        this.ancestors.add(ancestors);
        return this;
    }

    public FolderQueryElastic withAncestors(final List<String> ancestors) {
        this.ancestors.addAll(ancestors);
        return this;
    }

    public FolderQueryElastic withId(final List<String> id) {
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
        //by ancestors
        final Optional<JsonObject> ancestorsTerm = createTerm("ancestors", ancestors);
        if (ancestorsTerm.isPresent()) {
            filter.add(ancestorsTerm.get());
        }
        //trashed
        if (trashed != null) {
            filter.add(new JsonObject().put("term", new JsonObject().put("trashed", trashed)));
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
