package com.opendigitaleducation.explorer.plugin;

import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.Date;

public class ExplorerMessage {
    public enum ExplorerContentType{
        Text, Html, Pdf
    }
    public enum ExplorerAction{
        Upsert(ExplorerPriority.High),
        Delete(ExplorerPriority.High),
        Audience(ExplorerPriority.Low);
        private final ExplorerPriority priority;
        ExplorerAction(final ExplorerPriority i){
            this.priority = i;
        }

        public ExplorerPriority getPriority(boolean forSearch) {
            if(forSearch){
                return ExplorerPriority.Low;
            }
            return priority;
        }
    }

    public enum ExplorerPriority{
        High(1), Medium(0), Low(-1);
        private final int value;
        ExplorerPriority(final int i){
            this.value = i;
        }

        public int getValue() {
            return value;
        }
    }

    private final String id;
    private final String action;
    private final JsonObject message = new JsonObject();
    private final ExplorerPriority priority;
    private String idQueue;

    public ExplorerMessage(final String id, final String action, final ExplorerPriority priority) {
        this.id = id;
        this.action = action;
        this.priority = priority;
    }
    public ExplorerMessage(final String id, final ExplorerAction action, final boolean search) {
        this.id = id;
        this.action = action.name();
        this.priority = action.getPriority(search);
    }

    public static ExplorerMessage upsert(final String id, final UserInfos user, final boolean forSearch) {
        final ExplorerMessage builder = new ExplorerMessage(id, ExplorerAction.Upsert, forSearch);
        builder.message.put("createdAt", new Date().getTime());
        builder.message.put("creatorId", user.getUserId());
        builder.message.put("creatorName", user.getUsername());
        builder.message.put("updatedAt", new Date().getTime());
        builder.message.put("updaterId", user.getUserId());
        builder.message.put("updaterName", user.getUsername());
        return builder;
    }

    public static ExplorerMessage delete(final String id, final UserInfos user, final boolean forSearch) {
        final ExplorerMessage builder = new ExplorerMessage(id, ExplorerAction.Delete, forSearch);
        builder.message.put("deletedAt", new Date().getTime());
        builder.message.put("deleterId", user.getUserId());
        builder.message.put("deleterName", user.getUsername());
        return builder;
    }

    public ExplorerMessage withPublic(final boolean pub) {
        message.put("public", pub);
        return this;
    }

    public ExplorerMessage withTrashed(final boolean trashed){
        message.put("trashed", trashed);
        return this;
    }

    public ExplorerMessage withType(final String application, final String resourceType) {
        message.put("application", application);
        message.put("resourceType", resourceType);
        return this;
    }

    public ExplorerMessage withName(final String name) {
        message.put("name", name);
        return this;
    }

    public ExplorerMessage withContent(final String text, final ExplorerContentType type) {
        message.put("content", text);
        message.put("contentType", type.name());
        return this;
    }

    public ExplorerMessage withCustomFields(final JsonObject values) {
        message.put("custom", values);
        return this;
    }

    public ExplorerMessage withOverrideFields(final JsonObject values) {
        message.put("override", values);
        return this;
    }

    public ExplorerMessage withSubResourceContent(final String id, final String content, final ExplorerContentType type) {
        final JsonObject subResources = message.getJsonObject("subresources", new JsonObject());
        final JsonObject subResource = subResources.getJsonObject(id, new JsonObject());
        subResource.put("content", content);
        subResource.put("contentType", type.name());
        subResources.put(id, subResource);
        message.put("subresources", subResources);
        return this;
    }

    public ExplorerMessage withSubResourceHtml(final String id, final String content) {
        final JsonObject subResources = message.getJsonObject("subresources", new JsonObject());
        final JsonObject subResource = subResources.getJsonObject(id, new JsonObject());
        subResource.put("contentHtml", content);
        subResources.put(id, subResource);
        message.put("subresources", subResources);
        return this;
    }

    public JsonObject getMessage() {
        return message;
    }

    public String getId() {
        return id;
    }

    public String getCreatorId() {
        return message.getString("creatorId");
    }
    public String getApplication() {
        return message.getString("application");
    }

    public String getResourceType() {
        return message.getString("resourceType");
    }
    public JsonObject getOverride() {
        return message.getJsonObject("override");
    }

    public String getAction() {
        return action;
    }

    public ExplorerPriority getPriority() {
        return priority;
    }

    public String getResourceUniqueId() {
        return getId()+":"+getApplication()+":"+getResourceType();
    }

    public void setIdQueue(String idQueue) {
        this.idQueue = idQueue;
    }
}
