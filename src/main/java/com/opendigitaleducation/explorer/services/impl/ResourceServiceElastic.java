package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.ingest.MessageIngesterElastic;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class ResourceServiceElastic implements ResourceService {
    final ElasticClientManager manager;
    final ShareTableManager shareTableManager;
    final boolean waitFor = true;

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager) {
        this.manager = aManager;
        this.shareTableManager = shareTableManager;
    }

    protected String getIndex(final String application){
        return ExplorerConfig.getInstance().getIndex(application);
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos user, final String application, final SearchOperation operation) {
        return shareTableManager.findHashes(user).compose(hashes -> {
            final String index = getIndex(application);
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
            return manager.getClient().search(index, queryJson, options);
        });
    }

    @Override
    public Future<Integer> count(final UserInfos user, final String application, final SearchOperation operation) {
        return shareTableManager.findHashes(user).compose(hashes -> {
            final String index = getIndex(application);
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
            return manager.getClient().count(index, queryJson, options);
        });
    }

    @Override
    public Future<JsonObject> move(final UserInfos user, final String application, final JsonObject resource, final Optional<String> dest) {
        final String index = getIndex(application);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(getRoutingKey(resource));
        final StringBuilder scriptSource = new StringBuilder();
        final JsonObject params = new JsonObject();
        final JsonObject script = new JsonObject().put("lang", "painless").put("params", params);
        final JsonObject payload = new JsonObject().put("script", script);
        //TODO
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
        params.put("oldFolderId", "");
        params.put("newFolderId", dest.orElse(ExplorerConfig.ROOT_FOLDER_ID));
        params.put("userid", user.getUserId());
        //update
        return manager.getClient().updateDocument(index, resource.getString("_id"), payload, options).map(resource);
    }

    @Override
    public Future<JsonObject> share(final UserInfos user, final String application, final JsonObject resource, final List<ShareOperation> operation) throws Exception {
        return share(user, application, Arrays.asList(resource), operation).map(e -> e.iterator().next());
    }

    @Override
    public Future<List<JsonObject>> share(final UserInfos user, final String application, final List<JsonObject> resources, final List<ShareOperation> operation) throws Exception {
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
                scriptSource.append(String.format("ctx._source.visibleBy.removeIf(item -> item.indexOf('%s')==-1);", MessageIngesterElastic.VISIBLE_BY_CREATOR));
                scriptSource.append("if(!ctx._source.visibleBy.contains(params.hash))ctx._source.visibleBy.add(params.hash);");
                script.put("source", scriptSource.toString());
                //set params
                params.put("hash", hash.get());
                params.put("shared", new JsonArray(rights));
                final String index = getIndex(application);
                return manager.getClient().updateDocument(index, ids, payload, options);
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
