package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.services.FolderSearchOperation;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.math.NumberUtils;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.*;
import org.entcore.common.explorer.impl.ExplorerPlugin;
import org.entcore.common.explorer.to.*;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.opendigitaleducation.explorer.ExplorerConfig.ROOT_FOLDER_ID;


public class FolderServiceElastic implements FolderService {
    final ElasticClientManager manager;
    final FolderExplorerPlugin plugin;
    final FolderExplorerDbSql dbHelper;
    final MessageConsumer messageConsumer;

    public FolderServiceElastic(final ElasticClientManager aManager, final FolderExplorerPlugin plugin) {
        this.manager = aManager;
        this.plugin = plugin;
        this.dbHelper =  plugin.getDbHelper();
        this.messageConsumer = plugin.getCommunication().vertx().eventBus().consumer(ExplorerPlugin.FOLDERS_ADDRESS, message->{
            try {
                final String actionName = message.headers().get("action");
                final ExplorerPlugin.FolderActions action = ExplorerPlugin.FolderActions.valueOf(actionName);
                switch (action) {
                    case List: {
                        final JsonObject body = (JsonObject) message.body();
                        final FolderListRequest request = body.mapTo(FolderListRequest.class);
                        final UserInfos user = new UserInfos();
                        user.setGroupsIds(new ArrayList<>());
                        user.setUserId(request.getUserId());
                        user.setUsername(request.getUserName());
                        // search folders by user with max batch size (10000)
                        this.fetch(user, request.getApplication(), new FolderSearchOperation().setSearchEverywhere(true).setPageSize(10000l)).compose(result -> {
                            final Map<Long, FolderResponse> folders = result.stream().map(object -> {
                                final JsonObject json = (JsonObject) object;
                                final Long id = NumberUtils.toLong(json.getValue("_id").toString());
                                final String entId = json.getString("assetId");
                                final String name = json.getString("name");
                                final Long parentId = NumberUtils.toLong(json.getString("parentId"), -1l);
                                final Long safeParentId = parentId == -1l ? null : parentId;
                                final Boolean trashed = json.getBoolean("trashed");
                                final String creatorId = json.getString("creatorId");
                                final String creatorName = json.getString("creatorName");
                                final Long createdAt = json.getLong("createdAt");
                                final Long updatedAt = json.getLong("updatedAt");
                                final FolderResponse folder = new FolderResponse(id, name, entId, safeParentId, trashed, new ArrayList<>(), creatorId, creatorName, createdAt, updatedAt);
                                return folder;
                            }).collect(Collectors.toMap(FolderResponse::getId, Function.identity()));
                            // find subresources ids related to theses folders
                            final String index = ExplorerConfig.getInstance().getIndex(request.getApplication());
                            final ResourceQueryElastic query = new ResourceQueryElastic(user).withApplication(request.getApplication()).withSearchOperation(new ResourceSearchOperation().setFolderIds(folders.keySet()).setSearchEverywhere(true));
                            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(request.getApplication());
                            final JsonObject queryJson = query.withLimitedFieldNames(Arrays.asList("_id", "folderIds", "assetId")).getSearchQuery();
                            return manager.getClient().search(index, queryJson, options).map(resources -> {
                                // for each resource get folders related
                                for (final Object resource : resources) {
                                    final JsonObject json = (JsonObject) resource;
                                    final String entId = json.getString("assetId");
                                    final List<Long> folderIds = json.getJsonArray("folderIds", new JsonArray()).stream().map(e -> NumberUtils.toLong(e.toString())).collect(Collectors.toList());
                                    // add resources to related folders
                                    for (final Long folderId : folderIds) {
                                        final FolderResponse folder = folders.get(folderId);
                                        if (folder != null) {
                                            folder.getEntResourceIds().add(entId);
                                        }
                                    }
                                }
                                return folders.values();
                            });
                        }).onComplete(result -> {
                            if (result.succeeded()) {
                                final List<JsonObject> jsons = result.result().stream().map(e -> JsonObject.mapFrom(e)).collect(Collectors.toList());
                                final JsonArray jsonArray = new JsonArray(jsons);
                                message.reply(jsonArray);
                            } else {
                                message.fail(500, result.cause().getMessage());
                            }
                        });
                        break;
                    }
                    case Delete: {
                        final JsonObject body = (JsonObject) message.body();
                        final FolderDeleteRequest request = body.mapTo(FolderDeleteRequest.class);
                        final UserInfos user = new UserInfos();
                        user.setGroupsIds(new ArrayList<>());
                        user.setUserId(request.getUserId());
                        final Set<String> ids = request.getToDelete().stream().map(Object::toString).collect(Collectors.toSet());
                        this.delete(user, request.getApplication(), ids).onComplete(result -> {
                            if (result.succeeded()) {
                                final Set<Long> deleted = result.result().stream().map(e -> NumberUtils.toLong(e)).collect(Collectors.toSet());
                                final FolderDeleteResponse response = new FolderDeleteResponse(deleted);
                                final JsonObject json = JsonObject.mapFrom(response);
                                message.reply(json);
                            } else {
                                message.fail(500, result.cause().getMessage());
                            }
                        });
                        break;
                    }
                    case Upsert: {
                        final JsonObject body = (JsonObject) message.body();
                        final FolderUpsertRequest request = body.mapTo(FolderUpsertRequest.class);
                        final UserInfos user = new UserInfos();
                        user.setGroupsIds(new ArrayList<>());
                        user.setUserId(request.getUserId());
                        final JsonObject folder = new JsonObject().put("name", request.getName());
                        if (request.getTrashed() != null) {
                            folder.put("trashed", request.getTrashed());
                        }
                        if (request.getParentId() != null) {
                            folder.put("parentId", request.getParentId().toString());
                        }
                        folder.put("resourceType", ExplorerConfig.FOLDER_TYPE);
                        folder.put("entityType", ExplorerConfig.FOLDER_TYPE);
                        if(request.getParentId() == null){
                            folder.put("parentId", ROOT_FOLDER_ID);
                        }
                        if (request.getId() == null) {
                            this.create(user, request.getApplication(), folder).onComplete(result -> {
                                if (result.succeeded()) {
                                    final Long id = NumberUtils.toLong(result.result());
                                    final long now = new Date().getTime();
                                    final FolderResponse response = new FolderResponse(id, request.getName(), "", request.getParentId(), request.getTrashed(), new ArrayList<>(), user.getUserId(), user.getUsername(), now, now);
                                    final JsonObject json = JsonObject.mapFrom(response);
                                    message.reply(json);
                                } else {
                                    message.fail(500, result.cause().getMessage());
                                }
                            });
                        } else {
                            this.update(user, request.getId().toString(), request.getApplication(), folder).onComplete(result -> {
                                if (result.succeeded()) {
                                    final Long createdAt = result.result().getLong("createdAt");
                                    final String assetId = result.result().getString("assetId");
                                    final String name = Optional.ofNullable(request.getName()).orElse(result.result().getString("name"));
                                    final Long parentId = Optional.ofNullable(request.getParentId()).orElse(result.result().getLong("parentId"));
                                    final Boolean trashed = Optional.ofNullable(request.getTrashed()).orElse(result.result().getBoolean("trashed"));
                                    final long now = new Date().getTime();
                                    final FolderResponse response = new FolderResponse(request.getId(), name, assetId, parentId, trashed, new ArrayList<>(), user.getUserId(), user.getUsername(), createdAt, now);
                                    final JsonObject json = JsonObject.mapFrom(response);
                                    message.reply(json);
                                } else {
                                    message.fail(500, result.cause().getMessage());
                                }
                            });
                        }
                        break;
                    }
                    default:
                        message.fail(500, "Action not found");
                        break;
                }
            }catch(Exception e){
                message.fail(500, e.getMessage());
            }
        });
    }
    @Override
    public void stopConsumer() {
        this.messageConsumer.unregister();
    }

    protected String getIndex(){
        return ExplorerConfig.getInstance().getIndex(ExplorerConfig.FOLDER_APPLICATION);
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos creator, final Optional<String> application, final Optional<String> parentIdOpt) {
        final String parentId = parentIdOpt.orElse(ROOT_FOLDER_ID);
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId)
                .withParentId(parentId).withApplication(application).withSize(ExplorerConfig.DEFAULT_SIZE);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        final String index = getIndex();
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos creator, final String application, final FolderSearchOperation search) {
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId).withSearch(search).withApplication(application);
        final String index = getIndex();
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    @Override
    public Future<Integer> count(final UserInfos creator, final Optional<String> application, final FolderSearchOperation search) {
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId).withSearch(search).withApplication(application);
        final String index = getIndex();
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        final JsonObject queryJson = query.getSearchQuery();
        queryJson.remove("sort");
        return manager.getClient().count(index, queryJson, options);
    }

    protected String getRoutingKey(final UserInfos creator) {
        //TODO use application?
        return creator.getUserId();
    }

    @Override
    public Future<String> create(final UserInfos creator, final String application, final JsonObject folder) {
        return create(creator, application, Arrays.asList(folder)).map(e->{
            return e.get(0).getString("_id");
        });
    }

    @Override
    public Future<List<JsonObject>> create(final UserInfos creator, final String application, final List<JsonObject> folders) {
        final long now = System.currentTimeMillis();
        if(folders.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final Set<String> parentIds = folders.stream().filter(e->e.getValue("parentId") !=null).filter(e->{
            final String parentId = e.getValue("parentId").toString();
            return !(ROOT_FOLDER_ID.equalsIgnoreCase(parentId));
        }).map(e-> (e.getValue("parentId").toString())).collect(Collectors.toSet());
        final FolderSearchOperation search = new FolderSearchOperation().setIds(parentIds).setSearchEverywhere(true);
        final Set<Integer> parentIdAsNumbers = parentIds.stream().map(e -> NumberUtils.toInt(e,-1)).filter(e -> e!=-1).collect(Collectors.toSet());
        final Future<Integer> checkFuture = parentIdAsNumbers.isEmpty()?Future.succeededFuture(0):this.dbHelper.countByIds(parentIdAsNumbers);
        return checkFuture.compose(e->{
           if(e < parentIds.size()){
               return Future.failedFuture("folder.create.parent.invalid");
           }
           //force application
           for(final JsonObject folder : folders){
               folder.put("application", application);
           }
           plugin.setVersion(folders, now);
           return plugin.create(creator, folders, false).map(ids ->{
                for(int i = 0; i < folders.size(); i++){
                    folders.get(i).put("_id", ids.get(i));
                }
                return folders;
            });
        });
    }

    @Override
    public Future<JsonObject> update(final UserInfos creator, final String id, final String application, final JsonObject folder) {
        final long now = System.currentTimeMillis();
        final Set<String> parentIds = Collections.singletonList(folder).stream().filter(e->e.getValue("parentId") !=null).filter(e->{
            final String parentId = e.getValue("parentId").toString();
            return !(ROOT_FOLDER_ID.equalsIgnoreCase(parentId));
        }).map(e->{
            return (e.getValue("parentId").toString());
        }).collect(Collectors.toSet());
        final FolderSearchOperation search = new FolderSearchOperation().setIds(parentIds).setSearchEverywhere(true);
        final Future<Integer> checkFuture = parentIds.isEmpty()?Future.succeededFuture(0):count(creator,application,search);
        return checkFuture.compose(ee->{
            if(ee < parentIds.size()){
                return Future.failedFuture("folder.create.parent.invalid");
            }
            plugin.setIngestJobStateAndVersion(folder, IngestJobState.TO_BE_SENT, now);
            return plugin.update(creator, id, folder).map(e->{
                folder.put("_id", id);
                folder.put("updatedAt", new Date().getTime());
                return folder;
            });
        });
    }

    @Override
    public Future<List<String>> delete(final UserInfos creator, final String application, final Set<String> ids) {
        final long now = System.currentTimeMillis();
        if(ids.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        //CHECK IF HAVE RIGHTS
        final FolderSearchOperation search = new FolderSearchOperation().setIds(ids).setSearchEverywhere(true);
        final Future<Integer> checkFuture = ids.isEmpty()?Future.succeededFuture(0):count(creator,application,search);
        return checkFuture.compose(ee-> {
            if (ee < ids.size()) {
                return Future.failedFuture("folder.delete.id.invalid");
            }
            final Set<Integer> idInt = ids.stream().map(e-> Integer.valueOf(e)).collect(Collectors.toSet());
            return this.dbHelper.getDescendants(idInt).compose(descendants -> {
                final Set<Integer> all = new HashSet<>(idInt);
                for (final Map.Entry<String, FolderExplorerDbSql.FolderDescendant> s : descendants.entrySet()) {
                    all.add(Integer.valueOf(s.getKey()));
                    all.addAll(s.getValue().descendantIds.stream().map(e -> Integer.valueOf(e)).collect(Collectors.toSet()));
                }
                return this.dbHelper.getResourcesIdsForFolders(all).compose(resourcesIds -> {
                    final Set<Integer> resourceIdInt = resourcesIds.stream().map(e -> e.id).collect(Collectors.toSet());
                    //delete related resources
                    if(!resourceIdInt.isEmpty()){
                        this.dbHelper.getResourceHelper().getModelByIds(resourceIdInt).compose(models->{
                            final String resourceType = models.iterator().next().resourceType;
                            final Set<String> entIds = models.stream().map(e->e.entId).collect(Collectors.toSet());
                            final IExplorerPluginClient client = IExplorerPluginClient.withBus(plugin.getCommunication().vertx(), application, resourceType);
                            return client.deleteById(creator, entIds);
                        });
                    }
                    //delete folders
                    final List<String> idList = all.stream().map(e->e.toString()).collect(Collectors.toList());
                    return plugin.delete(creator, idList).map(idList);
                });
            });
        });
    }

    @Override
    public Future<List<JsonObject>> trash(final UserInfos creator, final Set<String> folderIds, final String application, final boolean isTrash) {
        final long now = System.currentTimeMillis();
        if(folderIds.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        //CHECK IF HAVE RIGHTS ON IT
        final Set<Integer> ids = folderIds.stream().map(e -> Integer.valueOf(e)).collect(Collectors.toSet());
        final FolderSearchOperation search = new FolderSearchOperation().setIds(folderIds).setSearchEverywhere(true);
        final Future<Integer> checkFuture = folderIds.isEmpty()?Future.succeededFuture(0):count(creator,application,search);
        return checkFuture.compose(ee-> {
            if (ee < folderIds.size()) {
                return Future.failedFuture("folder.trash.id.invalid");
            }
            return this.dbHelper.getDescendants(ids).compose(descendants -> {
                final Set<Integer> all = new HashSet<>(ids);
                for(final Map.Entry<String, FolderExplorerDbSql.FolderDescendant> s : descendants.entrySet()){
                    all.add(Integer.valueOf(s.getKey()));
                    all.addAll(s.getValue().descendantIds.stream().map(e->Integer.valueOf(e)).collect(Collectors.toSet()));
                }
                return this.dbHelper.getResourcesIdsForFolders(all).compose(resourcesIds ->{
                    final Set<Integer> resourceIdInt = resourcesIds.stream().map(e->e.id).collect(Collectors.toSet());
                    return this.dbHelper.trash(all, resourceIdInt, isTrash).compose(trashed -> {
                        final List<JsonObject> sources = new ArrayList<>();
                        for (final Integer key : all) {
                            final JsonObject source = new JsonObject().put("trashed", isTrash);
                            final FolderExplorerDbSql.FolderTrashResult trash = trashed.folders.get(key);
                            if(trash == null){
                                //folder does not exists anymore in postgres but exists in elastic
                                sources.add(plugin.setIdForModel(source.copy(), key.toString()));
                                continue;
                            }
                            final Optional<Integer> parentOpt = trash.parentId;
                            if (trash.application.isPresent()) {
                                source.put("application", trash.application.get());
                            }
                            //add
                            sources.add(plugin.setIdForModel(source.copy(), key.toString()));
                            //update children of oldParent
                            if (parentOpt.isPresent()) {
                                sources.add(plugin.setIdForModel(source.copy(), parentOpt.get().toString()));
                            }
                        }
                        plugin.setVersion(sources, now);
                        Future<Void> futureUpsertFolder = plugin.notifyUpsert(creator, sources);
                        //resources
                        final List<ExplorerMessage> messages = new ArrayList<>();
                        for(final FolderExplorerDbSql.FolderTrashResult trash : trashed.resources.values()){
                            //use entid to push resource message
                            // TODO JBER check version
                            final ExplorerMessage mess = ExplorerMessage.upsert(
                                    new IdAndVersion(trash.entId.get(), now), creator, false,
                                    trash.application.get(), trash.resourceType.get(), trash.resourceType.get()).withVersion(System.currentTimeMillis()).withSkipCheckVersion(true);
                            // TODO JBER check entityType
                            mess.withType(trash.application.get(), trash.resourceType.get(), trash.resourceType.get());
                            mess.withTrashed(isTrash);
                            messages.add(mess);
                        }
                        Future<Void> futureUpsertRes = plugin.getCommunication().pushMessage(messages);
                        //notify folders
                        return CompositeFuture.all(futureUpsertFolder, futureUpsertRes);
                    });
                });
            }).compose(e -> {
                return plugin.get(creator, folderIds);
            });
        });
    }

    @Override
    public Future<List<JsonObject>> move(final UserInfos creator, final Set<String> id, final String application, final Optional<String> destOrig) {
        final long now = System.currentTimeMillis();
        if(id.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        if(destOrig.isPresent() && ExplorerConfig.BIN_FOLDER_ID.equals(destOrig.get())){
            return this.trash(creator, id, application, true);
        }
        //CHECK IF HAVE RIGHTS ON IT
        final FolderSearchOperation search = new FolderSearchOperation().setIds(id).setSearchEverywhere(true);
        final Future<Integer> checkFuture = id.isEmpty()?Future.succeededFuture(0):count(creator,application,search);
        return checkFuture.compose(ee-> {
            if (ee < id.size()) {
                return Future.failedFuture("folder.move.id.invalid");
            }
            final Collection<Integer> ids = id.stream().map(e -> Integer.valueOf(e)).collect(Collectors.toSet());
            final Optional<String> dest = (destOrig.isPresent() && ExplorerConfig.ROOT_FOLDER_ID.equals(destOrig.get()))? Optional.empty():destOrig;
            return this.dbHelper.move(ids, dest).compose(oldParent->{
                final List<JsonObject> sources = new ArrayList<>();
                for(final Integer key : oldParent.keySet()){
                    final FolderExplorerDbSql.FolderMoveResult move = oldParent.get(key);
                    final Optional<Integer> parentOpt = move.parentId;
                    final JsonObject source = new JsonObject();
                    if(move.application.isPresent()){
                        source.put("application", move.application.get());
                    }
                    //add
                    sources.add(plugin.setIdForModel(source.copy(), key.toString()));
                    //update children of oldParent
                    if(parentOpt.isPresent()){
                        sources.add(plugin.setIdForModel(source.copy(), parentOpt.get().toString()));
                    }
                    //update children of newParent
                    if(dest.isPresent()){
                        sources.add(plugin.setIdForModel(source.copy(), dest.get()));
                    }
                }
                final Set<Integer> idAsInt = id.stream().map(e -> NumberUtils.toInt(e,-1)).filter(e -> e!= -1).collect(Collectors.toSet());
                return this.dbHelper.getDescendants(idAsInt).compose(descendants -> {
                    for(final FolderExplorerDbSql.FolderDescendant folderWithDescendant : descendants.values()) {
                        for(final String descendantId : folderWithDescendant.descendantIds) {
                            sources.add(plugin.setIdForModel(new JsonObject().put("application", application), descendantId));
                        }
                    }
                    plugin.setVersion(sources, now);
                    return plugin.notifyUpsert(creator, sources);
                });
            }).compose(e -> {
                return plugin.get(creator, id);
            });
        });
    }

    @Override
    public Future<JsonObject> move(final UserInfos creator, final String id, final String application, final Optional<String> dest) {
        return move(creator, new HashSet<>(Arrays.asList(id)),application, dest).compose(e->{
            if(e.isEmpty()){
                return Future.failedFuture("folder.notfound");
            }else{
                return Future.succeededFuture(e.get(0));
            }
        });
    }
}
