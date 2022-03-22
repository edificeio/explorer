package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.SearchOperation;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.impl.ExplorerPlugin;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class ResourceServiceElastic implements ResourceService {
    static Logger log = LoggerFactory.getLogger(ResourceServiceElastic.class);
    final ElasticClientManager manager;
    final ShareTableManager shareTableManager;
    final ResourceExplorerDbSql sql;
    final IExplorerPluginCommunication communication;
    final boolean waitFor = true;
    final MessageConsumer messageConsumer;

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager, final IExplorerPluginCommunication communication, final PostgresClient sql) {
        this(aManager, shareTableManager, communication, new ResourceExplorerDbSql(sql));
    }

    @Override
    public Future<Void> dropAll(final String application) {
        final String index = getIndex(application);
        log.info("Drop mapping for application="+application+", index="+index);
        return manager.getClient().deleteMapping(index);
    }

    @Override
    public Future<Void> init(final String application) {
        if(application.equalsIgnoreCase(ExplorerConfig.FOLDER_APPLICATION)){
            final String index = getIndex(application);
            log.info("Create mapping for application="+application+", index="+index);
            final Vertx vertx = communication.vertx();
            final Buffer mappingRes = vertx.fileSystem().readFileBlocking("es/mappingFolder.json");
            return manager.getClient().createMapping(index,mappingRes);
        }else{
            final String index = getIndex(application);
            log.info("Create mapping for application="+application+", index="+index);
            final Vertx vertx = communication.vertx();
            final Buffer mappingRes = vertx.fileSystem().readFileBlocking("es/mappingResource.json");
            return manager.getClient().createMapping(index,mappingRes);
        }
    }

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager, final IExplorerPluginCommunication communication, final ResourceExplorerDbSql sql) {
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
                           for(final ResourceExplorerDbSql.ResouceSql res : e.result()){
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
            final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withVisibleIds(hashes).withSearchOperation(operation);
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
            final JsonObject queryJson = query.getSearchQuery();
            return manager.getClient().search(index, queryJson, options);
        });
    }

    @Override
    public Future<FetchResult> fetchWithMeta(UserInfos user, String application, SearchOperation operation) {
        return shareTableManager.findHashes(user).compose(hashes -> {
            final String index = getIndex(application);
            final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withVisibleIds(hashes).withSearchOperation(operation);
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
            final JsonObject queryJson = query.getSearchQuery();
            return manager.getClient().searchWithMeta(index, queryJson, options);
        }).map(e -> {
            return new FetchResult(e.getCount(), e.getRows());
        });
    }

    @Override
    public Future<Integer> count(final UserInfos user, final String application, final SearchOperation operation) {
        return shareTableManager.findHashes(user).compose(hashes -> {
            final String index = getIndex(application);
            final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withVisibleIds(hashes).withSearchOperation(operation);
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
            final JsonObject queryJson = query.getSearchQuery();
            queryJson.remove("sort");
            return manager.getClient().count(index, queryJson, options);
        });
    }

    @Override
    public Future<JsonObject> move(final UserInfos user, final String application, final Integer id, final Optional<Integer> dest) {
        final Set<Integer> ids = new HashSet<>();
        ids.add(id);
        return move(user, application, ids, dest).map(e->{
            return e.getJsonObject(0);
        });
    }

    @Override
    public Future<JsonArray> move(final UserInfos user, final String application, final Set<Integer> ids, final Optional<Integer> dest) {
        if(ids.isEmpty()){
            return Future.succeededFuture(new JsonArray());
        }
        return count(user, application, new SearchOperation().setIdsInt(ids).setSearchEverywhere(true)).compose(count->{
            if(count < ids.size()){
                return Future.failedFuture("resource.move.id.invalid");
            }
            if(dest.isPresent()){
                return sql.moveTo(ids, dest.get(), user).compose(resources -> {
                    final List<ExplorerMessage> messages = resources.stream().map(e -> {
                        return ExplorerMessage.upsert(e.entId, user, false).withType(e.application, e.resourceType);
                    }).collect(Collectors.toList());
                    return communication.pushMessage(messages);
                }).compose(e->{
                    final SearchOperation search = new SearchOperation().setIds(ids.stream().map(id->id.toString()).collect(Collectors.toSet()));
                    return fetch(user, application, search);
                });
            }else{
                return sql.moveToRoot(ids, user).compose(entIds -> {
                    final List<ExplorerMessage> messages = entIds.stream().map(e -> {
                        return ExplorerMessage.upsert(e.entId, user, false).withType(e.application, e.resourceType);
                    }).collect(Collectors.toList());
                    return communication.pushMessage(messages);
                }).compose(e->{
                    final SearchOperation search = new SearchOperation().setIds(ids.stream().map(id->id.toString()).collect(Collectors.toSet()));
                    return fetch(user, application, search);
                });
            }
        });
    }

    @Override
    public Future<JsonObject> move(final UserInfos user, final String application, final JsonObject resource, final Optional<Integer> dest) {
        final Integer id = Integer.valueOf(resource.getString("_id"));
        final Set<Integer> ids = new HashSet<>();
        ids.add(id);
        return move(user, application, ids, dest).map(e->{
            return e.getJsonObject(0);
        });
    }

    @Override
    public Future<JsonArray> delete(final UserInfos user, final String application, final String resourceType,final Set<String> id) {
        if(id.isEmpty()){
            return Future.succeededFuture(new JsonArray());
        }
        return count(user, application, new SearchOperation().setId(id).setSearchEverywhere(true)).compose(count-> {
            if (count < id.size()) {
                return Future.failedFuture("resource.delete.id.invalid");
            }
            final String index = getIndex(application);
            final JsonObject payload = new ResourceQueryElastic(user).withId(id).getSearchQuery();
            final ElasticClient.ElasticOptions optios = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
            return manager.getClient().search(index, payload, optios).compose(e -> {
                final List<JsonObject> jsons = e.stream().map(j -> (JsonObject) j).collect(Collectors.toList());
                final Set<String> entIds = jsons.stream().map(j -> j.getString("entId")).collect(Collectors.toSet());
                final IExplorerPluginClient client = IExplorerPluginClient.withBus(communication.vertx(), application, resourceType);
                return client.deleteById(user, entIds).map(ee -> {
                    return new JsonArray(jsons);
                });
            });
        });
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
