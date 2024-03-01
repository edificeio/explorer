package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.IdAndVersion;
import org.entcore.common.explorer.impl.ExplorerPlugin;
import org.entcore.common.explorer.to.MuteRequest;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.share.ShareRoles;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;

public class ResourceServiceElastic implements ResourceService {
    static Logger log = LoggerFactory.getLogger(ResourceServiceElastic.class);
    final ElasticClientManager manager;
    final ShareTableManager shareTableManager;
    final ResourceExplorerDbSql sql;
    final IExplorerPluginCommunication communication;
    final MessageConsumer messageConsumer;

    private final MuteService muteService;

    public ResourceServiceElastic(final ElasticClientManager aManager,
                                  final ShareTableManager shareTableManager,
                                  final IExplorerPluginCommunication communication,
                                  final IPostgresClient sql,
                                  final MuteService muteService) {
        this(aManager, shareTableManager, communication, new ResourceExplorerDbSql(sql), muteService);
    }

    @Override
    public Future<Void> dropMapping(final String application) {
        final String index = getIndex(application);
        log.info("Drop mapping for application="+application+", index="+index);
        return manager.getClient().deleteMapping(index);
    }

    @Override
    public Future<Void> initMapping(final String application) {
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

    public ResourceServiceElastic(final ElasticClientManager aManager, final ShareTableManager shareTableManager,
                                  final IExplorerPluginCommunication communication, final ResourceExplorerDbSql sql,
                                  final MuteService muteService) {
        this.manager = aManager;
        this.sql = sql;
        this.communication = communication;
        this.shareTableManager = shareTableManager;
        this.muteService = muteService;
        this.messageConsumer = communication.vertx().eventBus().consumer(ExplorerPlugin.RESOURCES_ADDRESS, message->{
            final String actionName = message.headers().get("action");
            final ExplorerPlugin.ResourceActions action = ExplorerPlugin.ResourceActions.valueOf(actionName);
            switch (action) {
                case GetShares:
                    final JsonArray ids = (JsonArray)message.body();
                    final Set<String> idSet = ids.stream().map(e->e.toString()).collect(Collectors.toSet());
                    this.sql.getSharedByEntIds(idSet).onComplete(e->{
                       if(e.succeeded()){
                           final JsonObject results = new JsonObject();
                           for(final ResourceExplorerDbSql.ResouceSql res : e.result()){
                               results.put(res.entId, res.rights);
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
    public Future<JsonArray> fetch(final UserInfos user, final String application, final ResourceSearchOperation operation) {
        final String index = getIndex(application);
        final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withSearchOperation(operation);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
        final JsonObject queryJson = query.getSearchQuery();
        return manager.getClient().search(index, queryJson, options);
    }

    @Override
    public Future<FetchResult> fetchWithMeta(UserInfos user, String application, ResourceSearchOperation operation) {
        final String index = getIndex(application);
        final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withSearchOperation(operation);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
        final JsonObject queryJson = query.getSearchQuery();
        return manager.getClient().searchWithMeta(index, queryJson, options).map(e -> {
            return new FetchResult(e.getCount(), e.getRows());
        });
    }

    @Override
    public Future<Integer> count(final UserInfos user, final String application, final ResourceSearchOperation operation) {
        final String index = getIndex(application);
        final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(application).withSearchOperation(operation);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
        final JsonObject queryJson = query.getCountQuery();
        return manager.getClient().count(index, queryJson, options);
    }

    @Override
    public Future<JsonArray> trash(final UserInfos user, final String application, final Set<Integer> ids, final boolean isTrash) {
        final long now = currentTimeMillis();
        if(ids.isEmpty()){
            return Future.succeededFuture(new JsonArray());
        }
        //CHECK IF HAVE MANAGE RIGHTS
        final ResourceSearchOperation search = new ResourceSearchOperation().setIdsInt(ids).setSearchEverywhere(true);
        final Future<FetchResult> futureFetch = fetchWithMeta(user, application, search);
        return futureFetch.compose(fetch-> {
            if (fetch.count < ids.size()) {
                log.warn("User tried to trash resources that do not exist in OpenSearch. Expected to find " +
                        ids.size() + " elements but only " + fetch.count + " found");
                return Future.failedFuture("resource.trash.id.invalid");
            }
            final Set<Integer> idsToTrashForAll = new HashSet<>();
            final Set<IdAndVersion> idsToTrashForUserOnly = new HashSet<>();
            for (JsonObject row : fetch.rows) {
                if (isManager(row, user)) {
                    idsToTrashForAll.add(Integer.parseInt(row.getString("_id")));
                } else {
                    idsToTrashForUserOnly.add(new IdAndVersion(row.getString("assetId"), row.getLong("version")));
                }
            }
            //TODO remove previous parent if it is not trashed
            log.debug("Trashing (" + isTrash + ") " + idsToTrashForAll.size() + " resources for all");
            final Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trashedForAllFuture = sql.trashForAll(idsToTrashForAll, isTrash);
            log.debug("Trashing and muting (" + isTrash + ") " + idsToTrashForUserOnly.size() + " resources for user " + user.getUsername() + " only");
            final Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trashedForUserOnlyFuture = sql.trashForUser(idsToTrashForUserOnly, user.getUserId(), isTrash);
            final Future<List<ResourceExplorerDbSql.ResouceSql>> mutedForUserOnlyFuture = sql.muteResources(user.getUserId(), idsToTrashForUserOnly.stream().map(IdAndVersion::getId).collect(Collectors.toSet()), isTrash);

            return CompositeFuture.all(trashedForAllFuture, trashedForUserOnlyFuture, mutedForUserOnlyFuture).compose(result -> {
                List<ExplorerMessage> messagesToIngest = new ArrayList<>();
                // Ingestion messages for resources trashed or restored for all users
                messagesToIngest.addAll(trashedForAllFuture.result().values().stream()
                        .filter(folderTrashResult -> folderTrashResult.application.isPresent() && folderTrashResult.resourceType.isPresent())
                        .map(folderTrashResult -> ExplorerMessage.upsert(new IdAndVersion(folderTrashResult.entId.get(), now), user, false, folderTrashResult.application.get(), folderTrashResult.resourceType.get(), folderTrashResult.resourceType.get())
                                .withTrashed(isTrash)
                                .withVersion(now)
                                .withSkipCheckVersion(true))
                        .collect(Collectors.toList()));
                // Ingestion messages for resources trashed or restored for current user only
                messagesToIngest.addAll(trashedForUserOnlyFuture.result().values().stream()
                        .filter(folderTrashResult -> folderTrashResult.application.isPresent() && folderTrashResult.resourceType.isPresent())
                        .map(folderTrashResult -> ExplorerMessage.upsert(new IdAndVersion(folderTrashResult.entId.get(), now), user, false, folderTrashResult.application.get(), folderTrashResult.resourceType.get(), folderTrashResult.resourceType.get())
                                .withTrashedBy(folderTrashResult.trashedBy)
                                .withVersion(now)
                                .withSkipCheckVersion(true))
                        .collect(Collectors.toList()));
                return communication.pushMessage(messagesToIngest);
            }).compose(a -> {
                final ResourceSearchOperation search2 = new ResourceSearchOperation().setWaitFor(true).setIds(ids.stream().map(Object::toString).collect(Collectors.toSet()));
                return fetch(user, application, search2);
            });
        });
    }

    // TODO JBER move to a more central location
    /**
     * @param resource
     * @param user
     * @return
     */
    private boolean isManager(final JsonObject resource, final UserInfos user) {
        final String userId = user.getUserId();
        final JsonArray rights = resource.getJsonArray("rights");
        final boolean manage;
        if(rights == null) {
            // Check if we are the creator
            manage = userId.equals(resource.getString("creatorId"));
        } else {
            final Set<String> myAdminRights = new HashSet<>();
            myAdminRights.add(ShareRoles.Manager.getSerializedForUser(userId));
            myAdminRights.add(ShareRoles.getSerializedForCreator(userId));
            final List<String> groupIds = user.getGroupsIds();
            if(groupIds != null) {
                for (String groupId : user.getGroupsIds()) {
                    myAdminRights.add(ShareRoles.Manager.getSerializedForGroup(groupId));
                }
            }
            manage = rights.stream().anyMatch(myAdminRights::contains);
        }
        return manage;
    }

    @Override
    public Future<JsonObject> move(final UserInfos user, final String application, final Integer id, final Optional<String> dest) {
        final Set<Integer> ids = new HashSet<>();
        ids.add(id);
        return move(user, application, ids, dest).map(e->{
            return e.getJsonObject(0);
        });
    }

    @Override
    public Future<JsonArray> move(final UserInfos user, final String application, final Set<Integer> ids, final Optional<String> destOrig) {
        final long now = currentTimeMillis();
        if(ids.isEmpty()){
            return Future.succeededFuture(new JsonArray());
        }
        //TRASH
        if(destOrig.isPresent() && ExplorerConfig.BIN_FOLDER_ID.equals(destOrig.get())){
            return this.trash(user, application, ids, true);
        }
        //MOVE TO ROOT
        final Optional<String> dest = (destOrig.isPresent() && ExplorerConfig.ROOT_FOLDER_ID.equals(destOrig.get()))? Optional.empty():destOrig;
        //CHECK IF HAVE READ RIGHTS ON IT
        final Optional<Integer> destInt = dest.map(e-> Integer.valueOf(e));
        return count(user, application, new ResourceSearchOperation().setIdsInt(ids).setSearchEverywhere(true)).compose(count->{
            if(count < ids.size()){
                return Future.failedFuture("resource.move.id.invalid");
            }
            if(dest.isPresent()){
                return sql.moveTo(ids, destInt.get(), user).compose(resources -> {
                    final List<ExplorerMessage> messages = resources.stream().map(e -> {
                        //use entid to push message
                        return ExplorerMessage.upsert(new IdAndVersion(e.entId, now), user, false, e.application, e.resourceType, e.resourceType)
                                .withSkipCheckVersion(true);
                    }).collect(Collectors.toList());
                    return communication.pushMessage(messages);
                }).compose(e->{
                    final ResourceSearchOperation search = new ResourceSearchOperation().setWaitFor(true)
                            .setIds(ids.stream().map(Object::toString)
                            .collect(Collectors.toSet()));
                    return fetch(user, application, search);
                });
            }else{
                return sql.moveToRoot(ids, user).compose(entIds -> {
                    final List<ExplorerMessage> messages = entIds.stream().map(e -> {
                        //use entid to push message
                        return ExplorerMessage.upsert(new IdAndVersion(e.entId, now), user, false, e.application, e.resourceType, e.resourceType)
                                .withSkipCheckVersion(true);
                    }).collect(Collectors.toList());
                    return communication.pushMessage(messages);
                }).compose(e->{
                    final ResourceSearchOperation search = new ResourceSearchOperation().setWaitFor(true)
                            .setIds(ids.stream().map(Object::toString).collect(Collectors.toSet()));
                    return fetch(user, application, search);
                });
            }
        });
    }

    @Override
    public Future<JsonObject> move(final UserInfos user, final String application, final JsonObject resource, final Optional<String> dest) {
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
        //CHECK IF HAVE MANAGE RIGHTS
        //TODO we could make one call instead of making 2: count + search => only search and assert non empty
        final ResourceSearchOperation search = new ResourceSearchOperation().setIds(id).setSearchEverywhere(true).setRightType(ShareRoles.Manager);
        final Future<Integer> futureCheck = count(user, application, search);
        return futureCheck.compose(count-> {
            if (count < id.size()) {
                return Future.failedFuture("resource.delete.id.invalid");
            }
            final String index = getIndex(application);
            final JsonObject payload = new ResourceQueryElastic(user).withSearchOperation(search).getSearchQuery();
            final ElasticClient.ElasticOptions optios = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(application));
            return manager.getClient().search(index, payload, optios).compose(e -> {
                final List<JsonObject> jsons = e.stream().map(j -> (JsonObject) j).collect(Collectors.toList());
                final Set<String> entIds = jsons.stream().map(j -> j.getString("assetId")).collect(Collectors.toSet());
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
        final long now = currentTimeMillis();
        final List<JsonObject> rights = operation.stream().map(ShareOperation::toJsonRight).collect(Collectors.toList());
        final Set<String> normalizedRights = operation.stream().map(e -> e.getNormalizedRightsAsString()).collect(HashSet::new, Set::addAll, Set::addAll);
        final Set<Integer> ids = resources.stream().map(e -> Integer.valueOf(e.getString("_id"))).collect(Collectors.toSet());
        final JsonArray shared = new JsonArray(rights);
        return sql.getModelByIds(ids).compose(entIds -> {
            final List<ExplorerMessage> messages = entIds.stream().map(e -> {
                //use entid to push message
                return ExplorerMessage.upsert(new IdAndVersion(e.entId, now), user, false, e.application, e.resourceType, e.resourceType)
                        .withShared(shared, new ArrayList<>(normalizedRights))
                        .withSkipCheckVersion(true);
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
