package com.opendigitaleducation.explorer.services;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class FolderSearchOperation {
    private String id;
    private Boolean trashed;
    private boolean waitFor = false;
    private boolean searchEverywhere = false;
    private Set<String> ids = new HashSet<>();
    private Optional<String> parentId = Optional.empty();
    private Optional<Long> startIndex = Optional.empty();
    private Optional<Long> pageSize = Optional.empty();

    public boolean isWaitFor() {
        return waitFor;
    }

    public Optional<Long> getStartIndex() {return startIndex;}

    public FolderSearchOperation setStartIndex(final Long startIndex) {
        this.startIndex = Optional.ofNullable(startIndex);
        return this;
    }

    public Optional<Long> getPageSize() {return pageSize;}

    public FolderSearchOperation setPageSize(final Long pageSize) {
        this.pageSize = Optional.ofNullable(pageSize);
        return this;
    }

    public FolderSearchOperation setWaitFor(boolean waitFor) {
        this.waitFor = waitFor;
        return this;
    }

    public FolderSearchOperation setIds(Set<String> ids) {
        this.ids = ids;
        return this;
    }

    public Set<String> getIds() {
        return ids;
    }

    public FolderSearchOperation setId(String id) {
        this.id = id;
        return this;
    }

    public FolderSearchOperation setId(Object id) {
        if(id != null){
            this.id = id.toString();
        }
        return this;
    }

    public String getId() {
        return id;
    }

    public boolean isSearchEverywhere() {
        return searchEverywhere;
    }

    public FolderSearchOperation setSearchEverywhere(boolean searchEverywhere) {
        this.searchEverywhere = searchEverywhere;
        return this;
    }

    public Boolean getTrashed() {
        return trashed;
    }

    public FolderSearchOperation setTrashed(Boolean trashed) {
        this.trashed = trashed;
        return this;
    }

    public Optional<String> getParentId() {
        return parentId;
    }

    public FolderSearchOperation setParentId(Optional<String> parentId) {
        this.parentId = parentId;
        return this;
    }
    
    public FolderSearchOperation setParentId(Object parentId) {
        this.parentId = Optional.ofNullable(parentId).map(e -> e.toString());
        return this;
    }
}
