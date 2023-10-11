package com.opendigitaleducation.explorer.services;

import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.json.JsonArray;
import org.entcore.common.share.ShareRoles;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceSearchOperation {
    private Optional<String> orderField = Optional.empty();
    private Optional<Boolean> orderAsc = Optional.empty();
    private Optional<Long> startIndex = Optional.empty();
    private Optional<Long> pageSize = Optional.empty();
    private Optional<String> parentId = Optional.empty();
    private Optional<String> search = Optional.empty();
    private Optional<String> resourceType = Optional.empty();
    private Optional<Boolean> trashed = Optional.empty();
    private Optional<Boolean> pub = Optional.empty();
    private Optional<Boolean> shared = Optional.empty();
    private Optional<Boolean> owner = Optional.empty();
    private Optional<Boolean> favorite = Optional.empty();
    private Optional<String> id = Optional.empty();
    private Optional<String> rightType = Optional.empty();
    private Set<String> ids = new HashSet<>();
    private Set<Long> folderIds = new HashSet<>();
    private Optional<String> searchAfter = Optional.empty();
    private Optional<String> assetId = Optional.empty();
    private Set<String> assetIds = new HashSet<>();
    private boolean searchEverywhere = false;
    private boolean waitFor = false;

    public boolean isWaitFor() {
        return waitFor;
    }

    public Optional<String> getRightType() {
        return rightType;
    }

    public ResourceSearchOperation setRightType(final ShareRoles rightType) {
        this.rightType = Optional.ofNullable(rightType.key);
        return this;
    }

    public ResourceSearchOperation setRightType(final String rightType) {
        this.rightType = Optional.ofNullable(rightType);
        return this;
    }

    public ResourceSearchOperation setWaitFor(boolean waitFor) {
        this.waitFor = waitFor;
        return this;
    }

    public ResourceSearchOperation setIdsInt(Collection<Integer> ids) {
        this.ids = ids.stream().map(e->e.toString()).collect(Collectors.toSet());
        return this;
    }
    public ResourceSearchOperation setIds(Set<String> ids) {
        this.ids = ids;
        return this;
    }

    public Set<String> getIds() {
        return ids;
    }

    public ResourceSearchOperation setSearchAfter(final Object searchAfter) {
        this.searchAfter = Optional.ofNullable(searchAfter).map(e->e.toString());
        return this;
    }

    public Optional<String> getSearchAfter() {return searchAfter;}

    public Optional<Boolean> getOrderAsc() {return orderAsc;}

    public Optional<String> getOrderField() {return orderField;}

    public ResourceSearchOperation setOrder(final Optional<String> field, final Optional<Boolean> orderAsc) {
        this.orderAsc = orderAsc;
        this.orderField = field;
        return this;
    }

    public Optional<Long> getStartIndex() {return startIndex;}

    public ResourceSearchOperation setStartIndex(final Long startIndex) {
        this.startIndex = Optional.ofNullable(startIndex);
        return this;
    }

    public Optional<Long> getPageSize() {return pageSize;}

    public ResourceSearchOperation setPageSize(final Long pageSize) {
        this.pageSize = Optional.ofNullable(pageSize);
        return this;
    }

    public Optional<Boolean> getFavorite() {return favorite;}

    public ResourceSearchOperation setFavorite(final Boolean favorite) {
        this.favorite = Optional.ofNullable(favorite);
        return this;
    }
    public ResourceSearchOperation setFolderIds(final Set<Long> parentIds) {
        this.folderIds.addAll(parentIds);
        return this;
    }

    public Optional<Boolean> getPub() {return pub;}

    public ResourceSearchOperation setPub(final Boolean pub) {
        this.pub = Optional.ofNullable(pub);
        return this;
    }

    public Optional<Boolean> getShared() {return shared;}

    public Optional<Boolean> getOwner() {return owner;}

    public ResourceSearchOperation setOwner(final Boolean owner) {
        if(owner == null){
            this.owner = Optional.empty();
        }else{
            this.owner = Optional.ofNullable(owner);
        }
        return this;
    }
    public ResourceSearchOperation setShared(final Boolean shared) {
        if(shared == null){
            this.shared = Optional.empty();
        }else{
            this.shared = Optional.ofNullable(shared);
        }
        return this;
    }

    public Set<Long> getFolderIds() {
        return folderIds;
    }

    public ResourceSearchOperation setId(final String id) {
        this.id = Optional.ofNullable(id);
        return this;
    }

    public ResourceSearchOperation setId(final Object id) {
        this.id = Optional.ofNullable(id).map(e->e.toString());
        return this;
    }

    public ResourceSearchOperation setAssetId(final Object id) {
        if(id instanceof JsonArray){
            this.assetIds = ((JsonArray) id).stream().map(e -> e.toString()).collect(Collectors.toSet());
        }else{
            this.assetId = Optional.ofNullable(id).map(e->e.toString());
        }
        return this;
    }

    public Optional<String> getId() {
        return id;
    }

    public Optional<String> getAssetId() {
        return assetId;
    }

    public Set<String> getAssetIds() {
        return assetIds;
    }

    public boolean isSearchEverywhere() {
        return searchEverywhere;
    }

    public ResourceSearchOperation setSearchEverywhere(boolean searchEverywhere) {
        this.searchEverywhere = searchEverywhere;
        return this;
    }

    public Optional<Boolean> getTrashed() {
        return trashed;
    }

    public ResourceSearchOperation setTrashed(Boolean trashed) {
        this.trashed = Optional.ofNullable(trashed);
        return this;
    }

    public Optional<String> getResourceType() {return resourceType;}

    public ResourceSearchOperation setResourceType(final String resourceType) {
        this.resourceType = Optional.ofNullable(resourceType);
        return this;
    }

    public Optional<String> getParentId() {
        return parentId;
    }

    public ResourceSearchOperation setParentId(final String parentId) {
        this.parentId = Optional.ofNullable(parentId);
        return this;
    }
    public ResourceSearchOperation setParentId(final Object parentId) {
        if(parentId != null && ExplorerConfig.BIN_FOLDER_ID.equals(parentId.toString())){
            this.parentId = Optional.ofNullable(ExplorerConfig.ROOT_FOLDER_ID);
            this.trashed = Optional.ofNullable(true);
        }else{
            this.parentId = Optional.ofNullable(parentId).map(e-> e.toString());
        }
        return this;
    }

    public Optional<String> getSearch() {
        return search;
    }

    public ResourceSearchOperation setSearch(final Object search) {
        this.search = search==null?Optional.empty():Optional.ofNullable(search.toString());
        return this;
    }

    public ResourceSearchOperation setSearch(final String search) {
        this.search = Optional.ofNullable(search);
        return this;
    }
}
