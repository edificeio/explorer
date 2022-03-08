package com.opendigitaleducation.explorer.services;

import java.util.Optional;

public class SearchOperation {
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
    private Optional<Boolean> favorite = Optional.empty();
    private Optional<String> id = Optional.empty();
    private boolean searchEverywhere = false;

    public Optional<Boolean> getOrderAsc() {return orderAsc;}

    public Optional<String> getOrderField() {return orderField;}

    public SearchOperation setOrder(final Optional<String> field, final Optional<Boolean> orderAsc) {
        this.orderAsc = orderAsc;
        this.orderField = field;
        return this;
    }

    public Optional<Long> getStartIndex() {return startIndex;}

    public SearchOperation setStartIndex(final Long startIndex) {
        this.startIndex = Optional.ofNullable(startIndex);
        return this;
    }

    public Optional<Long> getPageSize() {return pageSize;}

    public SearchOperation setPageSize(final Long pageSize) {
        this.pageSize = Optional.ofNullable(pageSize);
        return this;
    }

    public Optional<Boolean> getFavorite() {return favorite;}

    public SearchOperation setFavorite(final Boolean favorite) {
        this.favorite = Optional.ofNullable(favorite);
        return this;
    }

    public Optional<Boolean> getPub() {return pub;}

    public SearchOperation setPub(final Boolean pub) {
        this.pub = Optional.ofNullable(pub);
        return this;
    }

    public Optional<Boolean> getShared() {return shared;}

    public SearchOperation setOwner(final Boolean owner) {
        if(owner == null){
            this.shared = Optional.empty();
        }else{
            this.shared = Optional.ofNullable(!owner);
        }
        return this;
    }
    public SearchOperation setShared(final Boolean shared) {
        this.shared = Optional.ofNullable(shared);
        return this;
    }

    public SearchOperation setId(final String id) {
        this.id = Optional.ofNullable(id);
        return this;
    }

    public Optional<String> getId() {
        return id;
    }

    public boolean isSearchEverywhere() {
        return searchEverywhere;
    }

    public SearchOperation setSearchEverywhere(boolean searchEverywhere) {
        this.searchEverywhere = searchEverywhere;
        return this;
    }

    public Optional<Boolean> getTrashed() {
        return trashed;
    }

    public SearchOperation setTrashed(Boolean trashed) {
        this.trashed = Optional.ofNullable(trashed);
        return this;
    }

    public Optional<String> getResourceType() {return resourceType;}

    public SearchOperation setResourceType(final String resourceType) {
        this.resourceType = Optional.ofNullable(resourceType);
        return this;
    }

    public Optional<String> getParentId() {
        return parentId;
    }

    public SearchOperation setParentId(final String parentId) {
        this.parentId = Optional.ofNullable(parentId);
        return this;
    }

    public Optional<String> getSearch() {
        return search;
    }

    public SearchOperation setSearch(final String search) {
        this.search = Optional.ofNullable(search);
        return this;
    }
}
