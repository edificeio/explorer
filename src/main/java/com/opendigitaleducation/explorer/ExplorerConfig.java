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

    public static final String ROOT_FOLDER_ID = "default";
    public static final String BIN_FOLDER_ID = "bin";
    public static final String FOLDER_APPLICATION = "explorer";
    public static final String FOLDER_TYPE = "folder";
    public static final String DEFAULT_FOLDER_INDEX = "folder";
    public static final String DEFAULT_RESOURCE_INDEX = "resource";
    public static final Integer DEFAULT_SIZE = 10000;
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

    public ExplorerConfig setEsIndex(final String application, final String index) {
        this.esIndexes.put(application, index);
        return this;
    }

    public static String getDefaultIndexName(final String application){
        return DEFAULT_RESOURCE_INDEX+"_"+application;
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
