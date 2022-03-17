package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.SearchOperation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;

public class ResourceQueryElastic {
    private final UserInfos user;
    private final List<Map.Entry<String, Boolean>> sorts = new ArrayList<>();
    private final List<String> resourceType = new ArrayList<>();
    private final List<String> application = new ArrayList<>();
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> folderId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private final List<String> visibleIds = new ArrayList<>();
    private final List<String> searchAfter = new ArrayList<>();
    private Optional<Long> from = Optional.empty();
    private Optional<Long> size = Optional.empty();
    private boolean onlyRoot = false;
    private Optional<Boolean> trashed = Optional.empty();
    private Optional<Boolean> favorite = Optional.empty();
    private Optional<Boolean> shared = Optional.empty();
    private Optional<Boolean> pub = Optional.empty();
    private Optional<String> text = Optional.empty();

    public ResourceQueryElastic(final UserInfos u) {
        this.user = u;
    }

    static <T> Optional<JsonObject> createTerm(final String key, final List<T> values) {
        if (values.size() == 0) {
            return Optional.empty();
        } else if (values.size() == 1) {
            return Optional.of(new JsonObject().put("term", new JsonObject().put(key, values.iterator().next())));
        } else {
            final JsonArray uniq = new JsonArray(new ArrayList(new HashSet<>(values)));
            return Optional.of(new JsonObject().put("terms", new JsonObject().put(key, uniq)));
        }
    }

    public ResourceQueryElastic withOrder(String name, Boolean asc) {
        this.sorts.add(new AbstractMap.SimpleEntry<>(name, asc));
        return this;
    }

    public ResourceQueryElastic withSearchAfter(String searchAfter) {
        this.searchAfter.add(searchAfter);
        return this;
    }

    public ResourceQueryElastic withShared(Boolean shared) {
        this.shared = Optional.ofNullable(shared);
        return this;
    }

    public ResourceQueryElastic withFavorite(Boolean favorite) {
        this.favorite = Optional.ofNullable(favorite);
        return this;
    }

    public ResourceQueryElastic withPub(Boolean pub) {
        this.pub = Optional.ofNullable(pub);
        return this;
    }

    public ResourceQueryElastic withTrashed(Boolean trashed) {
        this.trashed = Optional.ofNullable(trashed);
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
        this.text = Optional.ofNullable(text);
        return this;
    }

    public ResourceQueryElastic withApplication(String application) {
        this.application.add(application);
        return this;
    }

    public ResourceQueryElastic withResourceType(String resourceType) {
        this.resourceType.add(resourceType);
        return this;
    }

    public ResourceQueryElastic withFrom(Long from) {
        this.from = Optional.ofNullable(from);
        return this;
    }

    public ResourceQueryElastic withSize(Long size) {
        this.size = Optional.ofNullable(size);
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

    public ResourceQueryElastic withSearchOperation(final SearchOperation operation){
        if(operation.getId().isPresent()){
            this.withId(operation.getId().get());
        }
        if (operation.getParentId().isPresent()) {
            this.withFolderId(operation.getParentId().get());
        } else if (!operation.isSearchEverywhere()) {
            this.withOnlyRoot(true);
        }
        if (operation.getSearch().isPresent()) {
            this.withTextSearch(operation.getSearch().get());
        }
        if (operation.getTrashed().isPresent()) {
            this.withTrashed(operation.getTrashed().get());
        }
        if (operation.getResourceType().isPresent()) {
            this.withResourceType(operation.getResourceType().get());
        }
        if (operation.getShared().isPresent()) {
            this.withShared(operation.getShared().get());
        }
        if (operation.getOwner().isPresent()) {
            this.withShared(!operation.getOwner().get());
        }
        if (operation.getFavorite().isPresent()) {
            this.withFavorite(operation.getFavorite().get());
        }
        if (operation.getPub().isPresent()) {
            this.withPub(operation.getPub().get());
        }
        if (operation.getStartIndex().isPresent()) {
            this.withFrom(operation.getStartIndex().get());
        }
        if (operation.getPageSize().isPresent()) {
            this.withSize(operation.getPageSize().get());
        }
        if (operation.getOrderField().isPresent()) {
            this.withOrder(operation.getOrderField().get(), operation.getOrderAsc().orElse(true));
        }
        if(operation.getSearchAfter().isPresent()){
            this.withSearchAfter(operation.getSearchAfter().get());
        }
        return this;
    }

    public JsonObject getSearchQuery() {
        final JsonObject payload = new JsonObject();
        final JsonArray sort = new JsonArray();
        final JsonObject query = new JsonObject();
        final JsonObject bool = new JsonObject();
        final JsonArray filter = new JsonArray();
        final JsonArray mustNot = new JsonArray();
        final JsonArray must = new JsonArray();
        payload.put("sort", sort);
        payload.put("query", query);
        query.put("bool", bool);
        bool.put("filter", filter);
        bool.put("must_not", mustNot);
        bool.put("must", must);
        //by creator
        final Optional<JsonObject> creatorIdTerm = createTerm("creatorId", creatorId);
        if (creatorIdTerm.isPresent()) {
            must.add(creatorIdTerm.get());
        }
        //by visible
        {
            final List<String> visibles = new ArrayList<>();
            visibles.add(ExplorerConfig.getVisibleByCreator(user.getUserId()));
            visibles.add(ExplorerConfig.getVisibleByUser(user.getUserId()));
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
        //resourceType
        final Optional<JsonObject> resourceTypeTerm = createTerm("resourceType", resourceType);
        if (resourceTypeTerm.isPresent()) {
            filter.add(resourceTypeTerm.get());
        }
        //application
        final Optional<JsonObject> appTerm = createTerm("application", application);
        if (appTerm.isPresent()) {
            filter.add(appTerm.get());
        }
        //search text
        if (text.isPresent()) {
            final JsonArray fields = new JsonArray().add("application").add("contentAll");
            must.add(new JsonObject().put("multi_match", new JsonObject().put("query", text.get()).put("fields", fields)));
        }
        if (trashed.isPresent()) {
            filter.add(new JsonObject().put("term", new JsonObject().put("trashed", trashed.get())));
        }
        if (pub.isPresent()) {
            filter.add(new JsonObject().put("term", new JsonObject().put("public", pub.get())));
        }
        if (favorite.isPresent()) {
            if(favorite.get()){
                filter.add(new JsonObject().put("term", new JsonObject().put("favoriteFor", user.getUserId())));
            }else{
                mustNot.add(new JsonObject().put("term", new JsonObject().put("favoriteFor", user.getUserId())));
            }
        }
        if(shared.isPresent()){
            if(shared.get()){
                mustNot.add(new JsonObject().put("term", new JsonObject().put("creatorId", user.getUserId())));
            }else{
                filter.add(new JsonObject().put("term", new JsonObject().put("creatorId", user.getUserId())));
            }
        }
        //sort
        for(final Map.Entry<String, Boolean> s : this.sorts){
            sort.add(new JsonObject().put(s.getKey(), s.getValue()?"asc":"desc"));
        }
        //search after
        if(!searchAfter.isEmpty()){
            payload.put("search_after", new JsonArray(searchAfter));
        }
        //from / size
        if (from.isPresent() && searchAfter.isEmpty()) {
            payload.put("from", from.get());
        }
        if (size.isPresent()) {
            payload.put("size", size.get());
        }
        return payload;
    }
}
