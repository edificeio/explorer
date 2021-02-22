package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Optional;

//TODO add log profile
//TODO add metrics
public interface ResourceService {
    String CUSTOM_IDENTIFIER = "_identifier";
    String SUCCESS_FIELD = "_success";
    String ERROR_FIELD = "_error";
    String DEFAULT_RESOURCE_INDEX = "explorer_resource";

    static ResourceService.ResourceBulkOperationType getOperationType(final String type) {
        if (ExplorerService.RESOURCE_ACTION_DELETE.equals(type)) {
            return ResourceBulkOperationType.Delete;
        } else if (ExplorerService.RESOURCE_ACTION_UPDATE.equals(type)) {
            return ResourceBulkOperationType.Update;
        } else {
            return ResourceBulkOperationType.Create;
        }
    }

    <T> Future<List<JsonObject>> bulkOperations(final List<ResourceBulkOperation<T>> operations);

    //TODO fetch by other criterias...
    Future<JsonArray> fetch(final UserInfos user, final String application, final SearchOperation operation);

    Future<JsonObject> move(final UserInfos user, final JsonObject document, final Optional<String> source, final Optional<String> dest);

    Future<JsonObject> share(final UserInfos user, final JsonObject document, final List<ShareOperation> operation) throws Exception;

    /*
        static String getUserFolderId(UserInfos user, Optional<String> folderId) {
            return getUserFolderId(user.getUserId(), folderId);
        }

        static String getUserFolderId(String userId, Optional<String> folderId) {
            if(folderId.isPresent()) {
                return userId + ":" + folderId;
            }else{
                return userId + ":" + FolderService.ROOT_FOLDER_ID;
            }
        }
    */

    Future<List<JsonObject>> share(final UserInfos user, final List<JsonObject> documents, final List<ShareOperation> operation) throws Exception;

    enum ResourceBulkOperationType {
        Create, Update, Delete
    }

    class ResourceBulkOperation<T> {
        final JsonObject resource;
        final ResourceBulkOperationType type;
        final T customIdentifier;

        public ResourceBulkOperation(final JsonObject resource, final ResourceBulkOperationType type) {
            this(resource, type, null);
        }

        public ResourceBulkOperation(JsonObject resource, ResourceBulkOperationType type, final T customIdentifier) {
            this.resource = resource;
            this.type = type;
            this.customIdentifier = customIdentifier;
        }

        public JsonObject getResource() {
            return resource;
        }

        public ResourceBulkOperationType getType() {
            return type;
        }

        public T getCustomIdentifier() {
            return customIdentifier;
        }
    }

    class ShareOperation {
        final String id;
        final boolean group;
        final JsonObject rights;

        public ShareOperation(String id, boolean group, JsonObject rights) {
            this.id = id;
            this.group = group;
            this.rights = rights;
        }

        public String getId() {
            return id;
        }

        public boolean isGroup() {
            return group;
        }

        public JsonObject getRights() {
            return rights;
        }

        public JsonObject toJsonRight() {
            return this.rights.copy().put(group ? "groupId" : "userId", id);
        }
    }

    class SearchOperation {
        //TODO redirect setter to ElasticResourceQuery
        private Optional<String> parentId = Optional.empty();
        private String search;
        private Boolean trashed;
        private boolean searchEverywhere = false;

        public boolean isSearchEverywhere() {
            return searchEverywhere;
        }

        public SearchOperation setSearchEverywhere(boolean searchEverywhere) {
            this.searchEverywhere = searchEverywhere;
            return this;
        }

        public Boolean getTrashed() {
            return trashed;
        }

        public SearchOperation setTrashed(Boolean trashed) {
            this.trashed = trashed;
            return this;
        }

        public Optional<String> getParentId() {
            return parentId;
        }

        public SearchOperation setParentId(Optional<String> parentId) {
            this.parentId = parentId;
            return this;
        }

        public String getSearch() {
            return search;
        }

        public SearchOperation setSearch(String search) {
            this.search = search;
            return this;
        }
    }
}
