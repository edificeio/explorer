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

    public boolean isWaitFor() {
        return waitFor;
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
}
