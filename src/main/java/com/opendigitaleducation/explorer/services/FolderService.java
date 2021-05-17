package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface FolderService {
    String SUCCESS_FIELD = "_success";
    String ERROR_FIELD = "_error";

    //TODO fetch by application...
    Future<JsonArray> fetch(final UserInfos creator, final Optional<String> parentId);

    Future<JsonArray> fetch(final UserInfos creator, final FolderService.SearchOperation search);

    Future<Integer> count(final UserInfos creator, final FolderService.SearchOperation search);

    Future<String> create(final UserInfos creator, final JsonObject folder);

    Future<JsonObject> update(final UserInfos creator, final String id, final JsonObject folder);

    Future<List<String>> delete(final UserInfos creator, final Set<String> ids);

    Future<JsonObject> move(final UserInfos user, final JsonObject document, final Optional<String> source, final Optional<String> dest);

    Future<List<JsonObject>> create(final UserInfos creator, final List<JsonObject> folder);

    class SearchOperation {
        //TODO redirect setter to ElasticResourceQuery
        private Optional<String> parentId = Optional.empty();
        private Boolean trashed;
        private boolean searchEverywhere = false;
        private String id;

        public SearchOperation setId(String id) {
            this.id = id;
            return this;
        }

        public String getId() {
            return id;
        }

        public boolean isSearchEverywhere() {
            return searchEverywhere;
        }

        public FolderService.SearchOperation setSearchEverywhere(boolean searchEverywhere) {
            this.searchEverywhere = searchEverywhere;
            return this;
        }

        public Boolean getTrashed() {
            return trashed;
        }

        public FolderService.SearchOperation setTrashed(Boolean trashed) {
            this.trashed = trashed;
            return this;
        }

        public Optional<String> getParentId() {
            return parentId;
        }

        public FolderService.SearchOperation setParentId(Optional<String> parentId) {
            this.parentId = parentId;
            return this;
        }
    }
}
