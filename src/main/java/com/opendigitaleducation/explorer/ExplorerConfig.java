package com.opendigitaleducation.explorer;

import io.vertx.core.json.JsonObject;

import java.util.Set;

public class ExplorerConfig {
    private static ExplorerConfig instance = new ExplorerConfig();

    public static ExplorerConfig getInstance() {
        return instance;
    }

    private static final String VISIBLE_BY_CREATOR = "creator:";
    private static final String VISIBLE_BY_USER = "user:";
    private static final String VISIBLE_BY_GROUP = "group:";
    public static final String ROOT_FOLDER_ID = "root";
    public static final String FOLDER_APPLICATION = "explorer";
    public static final String FOLDER_TYPE = "folder";
    public static final String DEFAULT_FOLDER_INDEX = "folder";
    public static final String DEFAULT_RESOURCE_INDEX = "resource";
    protected JsonObject esIndexes = new JsonObject();

    public ExplorerConfig setEsIndexes(final JsonObject esIndexes) {
        this.esIndexes = esIndexes;
        return this;
    }
    public ExplorerConfig setEsIndex(final String application, final String index) {
        this.esIndexes.put(application, index);
        return this;
    }

    public static String getDefaultIndexName(final String application){
        return DEFAULT_RESOURCE_INDEX+"_"+application;
    }
    public static String getVisibleByCreator(String creatorId) {
        return VISIBLE_BY_CREATOR + creatorId;
    }
    public static String getVisibleByUser(String userId) {
        return VISIBLE_BY_USER + userId;
    }
    public static String getVisibleByGroup(String groupId) {
        return VISIBLE_BY_GROUP + groupId;
    }

    public String getIndex(final String application){
        //TODO one index per application?
        final String key = getDefaultIndexName(application);
        return esIndexes.getString(application, key);
    }

    public Set<String> getApplications() {
        return esIndexes.fieldNames();
    }
}
