package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface ResourceService {
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

    Future<List<JsonObject>> bulkOperations(final List<ResourceBulkOperation> operations);

    enum ResourceBulkOperationType {
        Create, Update, Delete
    }

    class ResourceBulkOperation {
        final JsonObject resource;
        final ResourceBulkOperationType type;

        public ResourceBulkOperation(JsonObject resource, ResourceBulkOperationType type) {
            this.resource = resource;
            this.type = type;
        }

        public JsonObject getResource() {
            return resource;
        }

        public ResourceBulkOperationType getType() {
            return type;
        }
    }
}
