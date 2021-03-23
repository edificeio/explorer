package com.opendigitaleducation.explorer.services.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ResourceQueryElastic {
    private final UserInfos user;
    private final List<String> application = new ArrayList<>();
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> folderId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private final List<String> visibleIds = new ArrayList<>();
    private Integer from;
    private Integer size;
    private boolean onlyRoot = false;
    private Boolean trashed;
    private String text;

    public ResourceQueryElastic(final UserInfos u) {
        this.user = u;
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

    public ResourceQueryElastic withTrashed(Boolean trashed) {
        this.trashed = trashed;
        return this;
    }

    public ResourceQueryElastic withVisibleIds(final Collection<String> ids) {
        visibleIds.addAll(ids);
        return this;
    }

    public ResourceQueryElastic withOnlyRoot(boolean onlyRoot) {
        this.onlyRoot = onlyRoot;
        return this;
    }

    public ResourceQueryElastic withTextSearch(String text) {
        this.text = text;
        return this;
    }

    public ResourceQueryElastic withApplication(String application) {
        this.application.add(application);
        return this;
    }

    public ResourceQueryElastic withFrom(Integer from) {
        this.from = from;
        return this;
    }

    public ResourceQueryElastic withSize(Integer size) {
        this.size = size;
        return this;
    }

    public ResourceQueryElastic withCreatorId(final String creatorId) {
        this.creatorId.add(creatorId);
        return this;
    }

    public ResourceQueryElastic withFolderId(final String folderId) {
        this.folderId.add(folderId);
        return this;
    }

    public ResourceQueryElastic withId(final String id) {
        this.id.add(id);
        return this;
    }

    public ResourceQueryElastic withId(final List<String> id) {
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
        final Optional<JsonObject> creatorIdTerm = createTerm("creatorId", creatorId);
        if (creatorIdTerm.isPresent()) {
            filter.add(creatorIdTerm.get());
        }
        //by visible
        {
            final List<String> visibles = new ArrayList<>();
            visibles.add(ResourceServiceElastic.getVisibleByCreator(user.getUserId()));
            visibles.addAll(this.visibleIds);
            final Optional<JsonObject> visibleTerm = createTerm("visibleBy", visibles);
            filter.add(visibleTerm.get());
        }
        //by folder
        if (onlyRoot) {
            mustNot.add(new JsonObject().put("term", new JsonObject().put("usersForFolderIds", user.getUserId())));
        } else {
            final Optional<JsonObject> folderIdsTerm = createTerm("folderIds", folderId);
            if (folderIdsTerm.isPresent()) {
                filter.add(folderIdsTerm.get());
            }
        }
        //by id
        final Optional<JsonObject> idTerm = createTerm("_id", id);
        if (idTerm.isPresent()) {
            filter.add(idTerm.get());
        }
        //application
        final Optional<JsonObject> appTerm = createTerm("application", application);
        if (appTerm.isPresent()) {
            filter.add(appTerm.get());
        }
        //search text
        if (text != null) {
            final JsonArray fields = new JsonArray().add("application").add("name").add("content");
            must.add(new JsonObject().put("multi_match", new JsonObject().put("query", text).put("fields", fields)));
        }
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
