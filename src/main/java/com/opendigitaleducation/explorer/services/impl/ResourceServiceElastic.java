package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.elastic.ElasticBulkRequest;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class ResourceServiceElastic implements ResourceService {
    private static final String VISIBLE_BY_CREATOR = "creator:";
    final ElasticClientManager manager;
    final ShareTableManager shareTableManager;
    final String index;
    final boolean waitFor = true;

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager) {
        this(aManager, shareTableManager, DEFAULT_RESOURCE_INDEX);
    }

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager, final String index) {
        this.manager = aManager;
        this.index = index;
        this.shareTableManager = shareTableManager;
    }

    static String getVisibleByCreator(String creatorId) {
        return VISIBLE_BY_CREATOR + creatorId;
    }

    public static JsonObject beforeCreate(final JsonObject document) {
        if (!document.containsKey("trashed")) {
            document.put("trashed", false);
        }
        if (!document.containsKey("public")) {
            document.put("public", false);
        }
        if (!document.containsKey("createdAt")) {
            document.put("createdAt", new Date().getTime());
        }
        if (document.containsKey("creatorId")) {
            document.put("visibleBy", new JsonArray().add(getVisibleByCreator(document.getString("creatorId"))));
        }
        if (!document.containsKey("visibleBy")) {
            document.put("visibleBy", new JsonArray());
        }
        if (!document.containsKey("folderIds")) {
            document.put("folderIds", new JsonArray().add(FolderService.ROOT_FOLDER_ID));
        }
        if (!document.containsKey("usersForFolderIds")) {
            document.put("usersForFolderIds", new JsonArray());
        }
        //custom field should not override existing fields
        final JsonObject custom = document.getJsonObject("custom", new JsonObject());
        for (final String key : custom.fieldNames()) {
            if (!document.containsKey(key)) {
                document.put(key, custom.getValue(key));
            }
        }
        document.remove("custom");
        return document;
    }

    public static JsonObject beforeUpdate(final JsonObject document) {
        //upsert should remove createdAt
        document.remove("createdAt");
        document.remove("creatorId");
        document.remove("creatorName");
        //
        if (!document.containsKey("trashed")) {
            document.put("trashed", false);
        }
        if (!document.containsKey("public")) {
            document.put("public", false);
        }
        document.put("updatedAt", new Date().getTime());
        //custom field should not override existing fields
        final JsonObject custom = document.getJsonObject("custom", new JsonObject());
        for (final String key : custom.fieldNames()) {
            if (!document.containsKey(key)) {
                document.put(key, custom.getValue(key));
            }
        }
        document.remove("custom");
        return document;
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos user, final String application, final SearchOperation operation) {
        return shareTableManager.findHashes(user).compose(hashes -> {
            final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withVisibleIds(hashes);
            if (operation.getParentId().isPresent()) {
                query.withFolderId(operation.getParentId().get());
            } else if (!operation.isSearchEverywhere()) {
                query.withOnlyRoot(true);
            }
            if (operation.getSearch() != null) {
                query.withTextSearch(operation.getSearch());
            }
            if (operation.getTrashed() != null) {
                query.withTrashed(operation.getTrashed());
            }
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
            final JsonObject queryJson = query.getSearchQuery();
            return manager.getClient().search(this.index, queryJson, options);
        });
    }

    @Override
    public Future<Integer> count(UserInfos user, String application, SearchOperation operation) {
        return shareTableManager.findHashes(user).compose(hashes -> {
            final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withVisibleIds(hashes);
            if (operation.getParentId().isPresent()) {
                query.withFolderId(operation.getParentId().get());
            } else if (!operation.isSearchEverywhere()) {
                query.withOnlyRoot(true);
            }
            if (operation.getSearch() != null) {
                query.withTextSearch(operation.getSearch());
            }
            if (operation.getTrashed() != null) {
                query.withTrashed(operation.getTrashed());
            }
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
            final JsonObject queryJson = query.getSearchQuery();
            return manager.getClient().count(this.index, queryJson, options);
        });
    }

    @Override
    public Future<JsonObject> move(final UserInfos user, final JsonObject resource, final Optional<String> source, final Optional<String> dest) {
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(getRoutingKey(resource));
        final StringBuilder scriptSource = new StringBuilder();
        final JsonObject params = new JsonObject();
        final JsonObject script = new JsonObject().put("lang", "painless").put("params", params);
        final JsonObject payload = new JsonObject().put("script", script);
        //build update script
        scriptSource.append("ctx._source.folderIds.removeIf(item -> item==params.oldFolderId);");
        scriptSource.append("if(!ctx._source.folderIds.contains(params.newFolderId)) ctx._source.folderIds.add(params.newFolderId);");
        if (dest.isPresent()) {
            //move to folder
            scriptSource.append("if(!ctx._source.usersForFolderIds.contains(params.userid)) ctx._source.usersForFolderIds.add(params.userid);");
        } else {
            //move to root
            scriptSource.append("ctx._source.usersForFolderIds.removeIf(item -> item == params.userid);");
        }
        script.put("source", scriptSource.toString());
        //set params
        params.put("oldFolderId", source.orElse(""));
        params.put("newFolderId", dest.orElse(FolderService.ROOT_FOLDER_ID));
        params.put("userid", user.getUserId());
        //update
        return manager.getClient().updateDocument(index, resource.getString("_id"), payload, options).map(resource);
    }

    @Override
    public Future<JsonObject> share(UserInfos user, JsonObject resource, List<ShareOperation> operation) throws Exception {
        return share(user, Arrays.asList(resource), operation).map(e -> e.iterator().next());
    }

    @Override
    public Future<List<JsonObject>> share(UserInfos user, List<JsonObject> resources, List<ShareOperation> operation) throws Exception {
        //TODO make a loop to avoid multiple loop
        final Set<String> groupIds = operation.stream().filter(e -> e.isGroup()).map(e -> e.getId()).collect(Collectors.toSet());
        final Set<String> userIds = operation.stream().filter(e -> !e.isGroup()).map(e -> e.getId()).collect(Collectors.toSet());
        final List<JsonObject> rights = operation.stream().map(o -> o.toJsonRight()).collect(Collectors.toList());
        final Set<String> ids = resources.stream().map(e -> e.getString("_id")).collect(Collectors.toSet());
        final Set<String> routings = resources.stream().map(e -> getRoutingKey(e)).collect(Collectors.toSet());
        return shareTableManager.getOrCreateNewShare(userIds, groupIds).compose(hash -> {
            if (hash.isPresent()) {
                final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(routings);
                final StringBuilder scriptSource = new StringBuilder();
                final JsonObject params = new JsonObject();
                final JsonObject script = new JsonObject().put("lang", "painless").put("params", params);
                final JsonObject payload = new JsonObject().put("script", script);
                //build update script
                scriptSource.append("ctx._source.shared=params.shared;");
                scriptSource.append(String.format("ctx._source.visibleBy.removeIf(item -> item.indexOf('%s')==-1);", VISIBLE_BY_CREATOR));
                scriptSource.append("if(!ctx._source.visibleBy.contains(params.hash))ctx._source.visibleBy.add(params.hash);");
                script.put("source", scriptSource.toString());
                //set params
                params.put("hash", hash.get());
                params.put("shared", new JsonArray(rights));
                return manager.getClient().updateDocument(this.index, ids, payload, options);
            } else {
                //user and groups are empty
                return Future.succeededFuture();
            }
        }).map(resources);
    }

    public static String getRoutingKey(final JsonObject resource) {
        return getRoutingKey(resource.getString("application"));
    }

    public static String getRoutingKey(final String application) {
        //TODO add resourceType?
        return application;
    }
}
