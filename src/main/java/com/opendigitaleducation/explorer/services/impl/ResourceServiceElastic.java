package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.eventbus.MessageConsumer;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import com.opendigitaleducation.explorer.folders.FolderExplorerCrudSql;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.folders.ResourceExplorerCrudSql;
import com.opendigitaleducation.explorer.ingest.MessageIngesterElastic;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.ExplorerPlugin;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.postgres.PostgresClient;
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
    final IExplorerPluginCommunication communication;
    final boolean waitFor = true;
    final MessageConsumer messageConsumer;

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager, final IExplorerPluginCommunication communication, final PostgresClient sql) {
        this(aManager, shareTableManager, communication, new ResourceExplorerCrudSql(sql));
    }
    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager, final IExplorerPluginCommunication communication, final ResourceExplorerCrudSql sql) {
        this.manager = aManager;
        this.sql = sql;
        this.communication = communication;
        this.shareTableManager = shareTableManager;
        this.messageConsumer = communication.vertx().eventBus().consumer(ExplorerPlugin.RESOURCES_ADDRESS, message->{
            final String action = message.headers().get("action");
            switch (action) {
                case ExplorerPlugin.RESOURCES_GETSHARE:
                    final JsonArray ids = (JsonArray)message.body();
                    final Set<String> idSet = ids.stream().map(e->e.toString()).collect(Collectors.toSet());
                    this.sql.getSharedByEntIds(idSet).onComplete(e->{
                       if(e.succeeded()){
                           final JsonObject results = new JsonObject();
                           for(final ResourceExplorerCrudSql.ResouceSql res : e.result()){
                               results.put(res.entId, res.shared);
                           }
                           message.reply(results);
                       }else{
                           message.fail(500, e.cause().getMessage());
                       }
                    });
                    break;
                default:
                    message.fail(500, "Action not found");
                    break;
            }
        });
    }

    @Override
    public void stopConsumer() {
        this.messageConsumer.unregister();
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
        return sql.getModelByIds(ids).compose(entIds -> {
            final List<ExplorerMessage> messages = entIds.stream().map(e -> {
                return ExplorerMessage.upsert(e.entId, user, false).withType(e.application, e.resourceType).withShared(shared);
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
