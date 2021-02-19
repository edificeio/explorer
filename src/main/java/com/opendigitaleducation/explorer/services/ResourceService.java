package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface ResourceService {
    String CUSTOM_IDENTIFIER = "_identifier";
    String SUCCESS_FIELD = "_success";
    String ERROR_FIELD = "_error";
    String DEFAULT_RESOURCE_INDEX = "explorer_resource";

    static String getUserFolderId(String userId, String folderId) {
        return userId + ":" + folderId;
    }

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
}
