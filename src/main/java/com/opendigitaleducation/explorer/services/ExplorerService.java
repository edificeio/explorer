package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.Date;
import java.util.List;

public interface ExplorerService {
    int PRIORITY_LOW = -1;
    int PRIORITY_DEFAULT = 0;
    int PRIORITY_HIGH = 1;
    String RESOURCE_ACTION_CREATE = "create";
    String RESOURCE_ACTION_UPDATE = "update";
    String RESOURCE_ACTION_DELETE = "delete";
    String RESOURCE_CHANNEL = "channel_resource";

    Future<Void> push(ExplorerMessageBuilder message);

    Future<Void> push(List<ExplorerMessageBuilder> messages);

    default <T> Future<Void> push(ExplorerMessageBuilder message, final Future<T> onSave) {
        return onSave.compose(e -> {
            return push(message);
        });
    }

    class ExplorerMessageBuilder {
        private final String id;
        private final String action;
        private final JsonObject message = new JsonObject();
        private int priority = ExplorerService.PRIORITY_DEFAULT;
        //TODO application mandatory? which are mandatory fields?
        public ExplorerMessageBuilder(final String id, final String action) {
            this.id = id;
            this.action = action;
        }

        public static ExplorerMessageBuilder create(String id, UserInfos user) {
            final ExplorerMessageBuilder builder = new ExplorerMessageBuilder(id, RESOURCE_ACTION_CREATE);
            builder.message.put("creatorId", user.getUserId());
            builder.message.put("creatorName", user.getUsername());
            builder.message.put("createdAt", new Date().getTime());
            return builder;
        }

        public static ExplorerMessageBuilder update(String id, UserInfos user) {
            final ExplorerMessageBuilder builder = new ExplorerMessageBuilder(id, RESOURCE_ACTION_UPDATE);
            return builder;
        }

        public static ExplorerMessageBuilder delete(String id, UserInfos user) {
            final ExplorerMessageBuilder builder = new ExplorerMessageBuilder(id, RESOURCE_ACTION_DELETE);
            return builder;
        }

        public ExplorerMessageBuilder withPublic(boolean pub) {
            message.put("public", pub);
            return this;
        }

        public ExplorerMessageBuilder withResourceType(String application, String resourceType) {
            message.put("application", application);
            message.put("resourceType", resourceType);
            return this;
        }

        public ExplorerMessageBuilder withName(String name) {
            message.put("name", name);
            return this;
        }

        public ExplorerMessageBuilder withContent(String text) {
            message.put("content", text);
            return this;
        }

        public ExplorerMessageBuilder withPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public JsonObject getMessage() {
            return message;
        }

        public String getId() {
            return id;
        }

        public String getAction() {
            return action;
        }

        public int getPriority() {
            return priority;
        }
    }
}
