package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;


public interface FolderService {

    default Future<JsonArray> fetch(final UserInfos creator, final String application, final Optional<String> parentId){
        return fetch(creator, Optional.ofNullable(application), parentId);
    }

    Future<JsonArray> fetch(final UserInfos creator, final Optional<String> application, final Optional<String> parentId);

    Future<JsonArray> fetch(final UserInfos creator, final String application, final FolderService.SearchOperation search);

    default Future<Integer> count(final UserInfos creator, final String application, final FolderService.SearchOperation search){
        return count(creator, Optional.ofNullable(application), search);
    }

    Future<Integer> count(final UserInfos creator, final Optional<String> application, final FolderService.SearchOperation search);

    Future<String> create(final UserInfos creator, final String application, final JsonObject folder);

    Future<List<JsonObject>> create(final UserInfos creator, final String application, final List<JsonObject> folder);

    Future<JsonObject> update(final UserInfos creator, final String id, final String application, final JsonObject folder);

    Future<List<String>> delete(final UserInfos creator, final String application, final Set<String> ids);

    Future<JsonObject> move(final UserInfos user, final String id, final String application, final Optional<String> dest);

    Future<List<JsonObject>> trash(UserInfos creator, Set<String> folderIds, String application, boolean isTrash);

    Future<List<JsonObject>> move(final UserInfos user, final Set<String> id, final String application, final Optional<String> dest);

    class SearchOperation {
        private String id;
        private Boolean trashed;
        private boolean searchEverywhere = false;
        private Set<String> ids = new HashSet<>();
        private Optional<String> parentId = Optional.empty();

        public SearchOperation setIds(Set<String> ids) {
            this.ids = ids;
            return this;
        }

        public Set<String> getIds() {
            return ids;
        }

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
