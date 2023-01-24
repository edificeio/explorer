package com.opendigitaleducation.explorer;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ExplorerConfig {
    private static ExplorerConfig instance = new ExplorerConfig();

    public static ExplorerConfig getInstance() {
        return instance;
    }

    private static final String VISIBLE_BY_CREATOR = "creator";
    private static final String VISIBLE_BY_USER = "user";
    private static final String VISIBLE_BY_GROUP = "group";
    public static final String ROOT_FOLDER_ID = "default";
    public static final String BIN_FOLDER_ID = "bin";
    public static final String FOLDER_APPLICATION = "explorer";
    public static final String FOLDER_TYPE = "folder";
    public static final String DEFAULT_FOLDER_INDEX = "folder";
    public static final String DEFAULT_RESOURCE_INDEX = "resource";
    public static final String RIGHT_READ = "read";
    public static final String RIGHT_CONTRIB = "contrib";
    public static final String RIGHT_MANAGE = "manage";
    protected JsonObject esIndexes = new JsonObject();
    protected boolean skipIndexOfTrashedFolders;
    protected Map<String, JsonObject> rightsByApplication = new HashMap<>();

    public boolean isSkipIndexOfTrashedFolders() {
        return skipIndexOfTrashedFolders;
    }

    public ExplorerConfig setSkipIndexOfTrashedFolders(boolean skipIndexOfTrashedFolders) {
        this.skipIndexOfTrashedFolders = skipIndexOfTrashedFolders;
        return this;
    }

    public ExplorerConfig setEsIndexes(final JsonObject esIndexes) {
        this.esIndexes = esIndexes;
        return this;
    }

    public ExplorerConfig setRightsByApplication(final JsonObject rightsByApplication) {
        for(final String key : rightsByApplication.fieldNames()){
            final JsonObject appConfig = rightsByApplication.getJsonObject(key);
            final JsonObject rights = appConfig.getJsonObject("rights");
            addRightsForApplication(key, rights);
        }
        return this;
    }

    public ExplorerConfig addRightsForApplication(final String application, final JsonObject rights) {
        rightsByApplication.put(application, rights);
        return this;
    }

    public Optional<String> getReadRightForApp(final String application) {
        if(application == null){
            return Optional.empty();
        }
        if(rightsByApplication.containsKey(application)){
            final JsonObject rights = rightsByApplication.get(application);
            return Optional.ofNullable(rights.getString(RIGHT_READ));
        }else{
            return Optional.empty();
        }
    }

    public Optional<String> getContribRightForApp(final String application) {
        if(application == null){
            return Optional.empty();
        }
        if(rightsByApplication.containsKey(application)){
            final JsonObject rights = rightsByApplication.get(application);
            return Optional.ofNullable(rights.getString(RIGHT_CONTRIB));
        }else{
            return Optional.empty();
        }
    }

    public Optional<String> getManageRightForApp(final String application) {
        if(application == null){
            return Optional.empty();
        }
        if(rightsByApplication.containsKey(application)){
            final JsonObject rights = rightsByApplication.get(application);
            return Optional.ofNullable(rights.getString(RIGHT_MANAGE));
        }else{
            return Optional.empty();
        }
    }

    public ExplorerConfig setEsIndex(final String application, final String index) {
        this.esIndexes.put(application, index);
        return this;
    }

    public static String getDefaultIndexName(final String application){
        return DEFAULT_RESOURCE_INDEX+"_"+application;
    }

    public static String getCreatorRight(final String creatorId) {
        return VISIBLE_BY_CREATOR + ":" + creatorId;
    }

    public static String getReadByUser(final String userId) {
        return getRightByUser(RIGHT_READ, userId);
    }

    public static String getReadByGroup(final String groupId) {
        return getRightByGroup(RIGHT_READ, groupId);
    }

    public static String getContribByUser(final String userId) {
        return getRightByUser(RIGHT_CONTRIB, userId);
    }

    public static String getContribByGroup(final String groupId) {
        return getRightByGroup(RIGHT_CONTRIB, groupId);
    }

    public static String getManageByUser(final String userId) {
        return getRightByUser(RIGHT_MANAGE, userId);
    }

    public static String getManageByGroup(final String groupId) {
        return getRightByGroup(RIGHT_MANAGE, groupId);
    }

    public static String getRightByUser(final String right, final String userId) {
        return VISIBLE_BY_USER + ":" + userId + ":" + right;
    }

    public static String getRightByGroup(final String right, final String groupId) {
        return VISIBLE_BY_GROUP + ":" + groupId + ":" + right;
    }

    public String getIndex(final String application){
        return getIndex(application, Optional.empty());
    }

    public String getIndex(final String application, final String type){
        return getIndex(application, Optional.ofNullable(type));
    }

    public String getIndex(final String application, final Optional<String> type){
        //TODO one index per application?
        if(type.isPresent() && FOLDER_TYPE.equalsIgnoreCase(type.get())){
            final String key = getDefaultIndexName(FOLDER_APPLICATION);
            return esIndexes.getString(FOLDER_APPLICATION, key);
        }
        final String key = getDefaultIndexName(application);
        return esIndexes.getString(application, key);
    }

    public Set<String> getApplications() {
        return esIndexes.fieldNames();
    }
}
