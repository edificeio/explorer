package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.FolderSearchOperation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class FolderQueryElastic {
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> parentId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private final List<String> ancestors = new ArrayList<>();
    private Optional<Integer> from = Optional.empty();
    private Optional<Integer> size = Optional.empty();
    private Optional<Boolean> trashed = Optional.empty();
    private Optional<String> application = Optional.empty();
    private Optional<String> text = Optional.empty();

    public FolderQueryElastic withTextSearch(String text) {
        this.text = Optional.ofNullable(text);
        return this;
    }

    public FolderQueryElastic withOnlyRoot(boolean onlyRoot) {
        this.parentId.clear();
        this.parentId.add(ExplorerConfig.ROOT_FOLDER_ID);
        return this;
    }

    public FolderQueryElastic withApplication(final Optional<String> app) {
        this.application = app;
        return this;
    }

    public FolderQueryElastic withApplication(final String app) {
        this.application = Optional.ofNullable(app);
        return this;
    }

    public FolderQueryElastic withTrashed(final Boolean trashed) {
        this.trashed = Optional.ofNullable(trashed);
        return this;
    }

    public FolderQueryElastic withFolderId(final String folderId) {
        this.parentId.add(folderId);
        return this;
    }

    static <T> Optional<JsonObject> createTerm(final String key, final T values) {
        return createTerm(key, Arrays.asList(values));
    }

    static <T> Optional<JsonObject> createTerm(final String key, final List<T> values) {
        if (values.size() == 0) {
            return Optional.empty();
        } else if (values.size() == 1) {
            return Optional.of(new JsonObject().put("term", new JsonObject().put(key, values.iterator().next())));
        } else {
            return Optional.of(new JsonObject().put("terms", new JsonObject().put(key, new JsonArray(values))));
        }
    }

    public FolderQueryElastic withFrom(final Integer from) {
        this.from = Optional.ofNullable(from);
        return this;
    }

    public FolderQueryElastic withSize(final Integer size) {
        this.size = Optional.ofNullable(size);
        return this;
    }

    public FolderQueryElastic withSearch(final FolderSearchOperation search){
        if (search.getParentId().isPresent()) {
            this.withFolderId(search.getParentId().get());
        } else if (!search.isSearchEverywhere()) {
            this.withOnlyRoot(true);
        }
        if (search.getTrashed() != null) {
            this.withTrashed(search.getTrashed());
        }
        if(search.getId() != null){
            this.withId(search.getId());
        }
        if(!search.getIds().isEmpty()){
            this.id.addAll(search.getIds());
        }
        if (search.getStartIndex().isPresent()) {
            this.withFrom(search.getStartIndex().get().intValue());
        }
        if (search.getPageSize().isPresent()) {
            this.withSize(search.getPageSize().get().intValue());
        }
        if (search.getSearch().isPresent()) {
            this.withTextSearch(search.getSearch().get());
        }
        return this;
    }

    public FolderQueryElastic withCreatorId(final String creatorId) {
        this.creatorId.add(creatorId);
        return this;
    }

    public FolderQueryElastic withParentId(final String parentId) {
        if(ExplorerConfig.BIN_FOLDER_ID.equals(parentId)){
            this.parentId.add(ExplorerConfig.ROOT_FOLDER_ID);
            this.trashed = Optional.ofNullable(true);
        }else{
            this.parentId.add(parentId);
        }
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
        final JsonArray must = new JsonArray();
        payload.put("query", query);
        query.put("bool", bool);
        bool.put("filter", filter);
        bool.put("must", must);
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
        //application
        if(application.isPresent()){
            final Optional<JsonObject> appTerm = createTerm("application", application.get());
            if (appTerm.isPresent()) {
                filter.add(appTerm.get());
            }
        }
        //search text
        if (text.isPresent()) {
            final JsonObject prefix = new JsonObject();
            prefix.put("query", text.get());
            must.add(new JsonObject().put("match_phrase_prefix", new JsonObject().put("contentAll", prefix)));
        }
        //trashed
        if (trashed.isPresent()) {
            filter.add(new JsonObject().put("term", new JsonObject().put("trashed", trashed.get())));
        }
        //from / size
        if (from.isPresent()) {
            payload.put("from", from.get());
        }
        if (size.isPresent()) {
            payload.put("size", size.get());
        }
        return payload;
    }
}
