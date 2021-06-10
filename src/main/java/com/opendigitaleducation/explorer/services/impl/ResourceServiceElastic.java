package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.folders.FolderExplorerCrudSql;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.folders.ResourceExplorerCrudSql;
import com.opendigitaleducation.explorer.ingest.MessageIngesterElastic;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunication;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
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
    final ResourceExplorerCrudSql sql;
    final ExplorerPluginCommunication communication;
    final boolean waitFor = true;

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager, final ExplorerPluginCommunication communication, final PostgresClient sql) {
        this(aManager, shareTableManager, communication, new ResourceExplorerCrudSql(sql));
    }
    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager, final ExplorerPluginCommunication communication, final ResourceExplorerCrudSql sql) {
        this.manager = aManager;
        this.sql = sql;
        this.communication = communication;
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
    public Future<JsonObject> move(final UserInfos user, final String application, final JsonObject resource, final Optional<Integer> dest) {
        final Integer id = Integer.valueOf(resource.getString("_id"));
        final Set<Integer> ids = new HashSet<>();
        ids.add(id);
        if(dest.isPresent()){
            return sql.moveTo(ids, dest.get(), user).compose(resources -> {
                final List<ExplorerMessage> messages = resources.stream().map(e -> {
                    return ExplorerMessage.upsert(e.entId, user, false).withType(e.application, e.resourceType);
                }).collect(Collectors.toList());
                return communication.pushMessage(messages);
            }).map(resource);
        }else{
            return sql.moveToRoot(ids, user).compose(entIds -> {
                final List<ExplorerMessage> messages = entIds.stream().map(e -> {
                    return ExplorerMessage.upsert(e.entId, user, false).withType(e.application, e.resourceType);
                }).collect(Collectors.toList());
                return communication.pushMessage(messages);
            }).map(resource);
        }
    }

    @Override
    public Future<JsonObject> share(final UserInfos user, final String application, final JsonObject resource, final List<ShareOperation> operation) throws Exception {
        return share(user, application, Arrays.asList(resource), operation).map(e -> e.iterator().next());
    }

    @Override
    public Future<List<JsonObject>> share(final UserInfos user, final String application, final List<JsonObject> resources, final List<ShareOperation> operation) throws Exception {
        final List<JsonObject> rights = operation.stream().map(o -> o.toJsonRight()).collect(Collectors.toList());
        final Set<Integer> ids = resources.stream().map(e -> Integer.valueOf(e.getString("_id"))).collect(Collectors.toSet());
        final JsonArray shared = new JsonArray(rights);
        return sql.updateShareById(ids, shared).compose(entIds -> {
            final List<ExplorerMessage> messages = entIds.stream().map(e -> {
                return ExplorerMessage.upsert(e.entId, user, false).withType(e.application, e.resourceType);
            }).collect(Collectors.toList());
            return communication.pushMessage(messages);
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