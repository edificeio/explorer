package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.entcore.common.share.ShareRoles;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class ResourceQueryElastic {
    private final Optional<UserInfos> user;
    private final List<Map.Entry<String, Boolean>> sorts = new ArrayList<>();
    private final List<String> resourceType = new ArrayList<>();
    private final List<String> application = new ArrayList<>();
    private final List<String> creatorId = new ArrayList<>();
    private final List<String> folderId = new ArrayList<>();
    private final List<String> id = new ArrayList<>();
    private final List<String> rights = new ArrayList<>();
    private final List<String> searchAfter = new ArrayList<>();
    private Optional<Long> from = Optional.empty();
    private Optional<Long> size = Optional.empty();
    private boolean onlyRoot = false;
    private Optional<Boolean> trashed = Optional.empty();
    private Optional<Boolean> favorite = Optional.empty();
    private Optional<Boolean> shared = Optional.empty();
    private Optional<Boolean> pub = Optional.empty();
    private Optional<String> text = Optional.empty();
    private Optional<String> userRightType = Optional.empty();
    private List<String> selectFields = new ArrayList<>();
    private List<String> assetId = new ArrayList<>();

    public ResourceQueryElastic(final UserInfos u) {
        this.user = Optional.ofNullable(u);
    }

    public ResourceQueryElastic(final Optional<UserInfos> u) {
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

    public ResourceQueryElastic withRightType(final String rightType) {
        this.userRightType = Optional.ofNullable(rightType);
        return this;
    }

    public ResourceQueryElastic withRightType(final ShareRoles rightType) {
        return this.withRightType(rightType.key);
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

    public ResourceQueryElastic withRights(final Collection<String> ids) {
        rights.addAll(ids);
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

    public ResourceQueryElastic withFolderIds(final Set<String> folderId) {
        this.folderId.addAll(folderId);
        return this;
    }

    public ResourceQueryElastic withId(final String id) {
        this.id.add(id);
        return this;
    }

    public ResourceQueryElastic withId(final Collection<String> id) {
        this.id.addAll(id);
        return this;
    }

    public ResourceQueryElastic withAssetId(final String id) {
        this.assetId.add(id);
        return this;
    }

    public ResourceQueryElastic withAssetId(final Collection<String> id) {
        this.assetId.addAll(id);
        return this;
    }

    public ResourceQueryElastic withLimitedFieldNames(final Collection<String> names) {
        this.selectFields.addAll(names);
        return this;
    }

    public ResourceQueryElastic withRight(final String id) {
        this.rights.add(id);
        return this;
    }

    public ResourceQueryElastic withUserRight(final String right, final String userId) {
        return withRight(ShareRoles.getSerializedForUserAndRole(userId, right));
    }

    public ResourceQueryElastic withGroupRight(final String right, final String groupId) {
        return withRight(ShareRoles.getSerializedForGroupAndRole(groupId, right));
    }

    public ResourceQueryElastic withUserInfoRights(final String right, final UserInfos user) {
        withUserRight(right, user.getUserId());
        for(final String id : user.getGroupsIds()){
            withGroupRight(right, id);
        }
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

    public ResourceQueryElastic withSearchOperation(final ResourceSearchOperation operation){
        if(operation.getId().isPresent()){
            this.withId(operation.getId().get());
        }
        if(!operation.getIds().isEmpty()){
            this.withId(operation.getIds());
        }
        if(operation.getAssetId().isPresent()){
            this.withAssetId(operation.getAssetId().get());
        }
        if(!operation.getAssetIds().isEmpty()){
            this.withAssetId(operation.getAssetIds());
        }
        if (operation.getParentId().isPresent() && !ExplorerConfig.ROOT_FOLDER_ID.equalsIgnoreCase(operation.getParentId().get())) {
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
        if(operation.getRightType().isPresent()){
            this.userRightType = operation.getRightType();
        }
        if(!operation.getFolderIds().isEmpty()){
            this.folderId.addAll(operation.getFolderIds().stream().map(Object::toString).collect(Collectors.toSet()));
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
        final JsonArray should = new JsonArray();
        payload.put("sort", sort);
        payload.put("query", query);
        query.put("bool", bool);
        bool.put("filter", filter);
        bool.put("must_not", mustNot);
        bool.put("must", must);
        bool.put("should", should);
        // select fields
        if(!this.selectFields.isEmpty()){
            payload.put("_source", new JsonArray(this.selectFields));
        }
        //by creator
        final Optional<JsonObject> creatorIdTerm = createTerm("creatorId", creatorId);
        if (creatorIdTerm.isPresent()) {
            must.add(creatorIdTerm.get());
        }
        //by rights
        if(user.isPresent()){
            final UserInfos user = this.user.get();
            final List<String> rights = new ArrayList<>();
            //by creator
            rights.add(ShareRoles.getSerializedForCreator(user.getUserId()));
            //by user id
            if(this.userRightType.isPresent()){
                rights.add(ShareRoles.getSerializedForUserAndRole(user.getUserId(), this.userRightType.get()));
            }else{
                rights.add(ShareRoles.Read.getSerializedForUser(user.getUserId()));
            }
            //by group ids
            for(final String groupId : user.getGroupsIds()){
                if(this.userRightType.isPresent()){
                    rights.add(ShareRoles.getSerializedForGroupAndRole(groupId, this.userRightType.get()));
                }else{
                    rights.add(ShareRoles.Read.getSerializedForGroup(groupId));
                }
            }
            //create term
            final Optional<JsonObject> rightsTerm = createTerm("rights", rights);
            filter.add(rightsTerm.get());
        }else if(!this.rights.isEmpty()){
            //force rights
            final List<String> rights = new ArrayList<>();
            rights.addAll(this.rights);
            final Optional<JsonObject> rightsTerm = createTerm("rights", rights);
            filter.add(rightsTerm.get());
        }
        //by folder
        if (onlyRoot) {
            if(user.isPresent()){
                mustNot.add(new JsonObject().put("term", new JsonObject().put("usersForFolderIds", user.get().getUserId())));
            }
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
        //by asset id
        final Optional<JsonObject> assetIdTerm = createTerm("assetId", assetId);
        if (assetIdTerm.isPresent()) {
            filter.add(assetIdTerm.get());
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
        if (text.isPresent() && !text.get().isEmpty()) {
            if(StringUtils.containsWhitespace(text.get())){
                // contains multiple words
                final JsonObject prefix = new JsonObject();
                prefix.put("query", text.get());
                // allow 3 words between terms
                prefix.put("slop", 3);
                must.add(new JsonObject().put("match_phrase_prefix", new JsonObject().put("contentAll", prefix)));
            }else{
                // contains only one word
                final JsonObject prefix = new JsonObject();
                prefix.put("value", text.get());
                prefix.put("case_insensitive", true);
                must.add(new JsonObject().put("prefix", new JsonObject().put("contentAll", prefix)));
            }
        }
        if (trashed.isPresent()) {
            if(trashed.get()){
                should.add(new JsonObject().put("term", new JsonObject().put("trashed", true)));
                if(this.user.isPresent()){
                    should.add(new JsonObject().put("term", new JsonObject().put("trashedBy", this.user.get().getUserId())));
                }
                // should match at least one of theses conditions
                bool.put("minimum_should_match", 1);
            }else{
                mustNot.add(new JsonObject().put("term", new JsonObject().put("trashed", true)));
                if(this.user.isPresent()){
                    mustNot.add(new JsonObject().put("term", new JsonObject().put("trashedBy", this.user.get().getUserId())));
                }
            }
        }
        if (pub.isPresent()) {
            filter.add(new JsonObject().put("term", new JsonObject().put("public", pub.get())));
        }
        if(user.isPresent()) {
            if (favorite.isPresent()) {
                if (favorite.get()) {
                    filter.add(new JsonObject().put("term", new JsonObject().put("favoriteFor", user.get().getUserId())));
                } else {
                    mustNot.add(new JsonObject().put("term", new JsonObject().put("favoriteFor", user.get().getUserId())));
                }
            }
            if (shared.isPresent()) {
                if (shared.get()) {
                    mustNot.add(new JsonObject().put("term", new JsonObject().put("creatorId", user.get().getUserId())));
                } else {
                    filter.add(new JsonObject().put("term", new JsonObject().put("creatorId", user.get().getUserId())));
                }
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
        }else if(!id.isEmpty()) {
            // if searching by id => size must be at least id.size
            payload.put("size", id.size()+1);
        }else if(!assetId.isEmpty()) {
            // if searching by assetId => size must be at least assetId.size
            payload.put("size", assetId.size()+1);
        }
        return payload;
    }

    public JsonObject getCountQuery() {
        final JsonObject payload = this.getSearchQuery();
        payload.remove("from");
        payload.remove("size");
        payload.remove("sort");
        return payload;
    }
}
