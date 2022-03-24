package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.services.FolderService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

import static com.opendigitaleducation.explorer.ExplorerConfig.ROOT_FOLDER_ID;


public class FolderServiceElastic implements FolderService {
    final ElasticClientManager manager;
    final FolderExplorerPlugin plugin;

    public FolderServiceElastic(final ElasticClientManager aManager, final FolderExplorerPlugin plugin) {
        this.manager = aManager;
        this.plugin = plugin;
    }

    protected String getIndex(){
        return ExplorerConfig.getInstance().getIndex(ExplorerConfig.FOLDER_APPLICATION);
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos creator, final Optional<String> application, final Optional<String> parentIdOpt) {
        final String parentId = parentIdOpt.orElse(ROOT_FOLDER_ID);
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId).withParentId(parentId).withApplication(application);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        final String index = getIndex();
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos creator, final String application, final SearchOperation search) {
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId).withSearch(search).withApplication(application);
        final String index = getIndex();
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    @Override
    public Future<Integer> count(final UserInfos creator, final Optional<String> application, final SearchOperation search) {
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
        if(folders.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final Set<String> parentIds = folders.stream().filter(e->e.getValue("parentId") !=null).filter(e->{
            final String parentId = e.getValue("parentId").toString();
            return !(ROOT_FOLDER_ID.equalsIgnoreCase(parentId));
        }).map(e->{
            return (e.getValue("parentId").toString());
        }).collect(Collectors.toSet());
        final SearchOperation search = new SearchOperation().setIds(parentIds).setSearchEverywhere(true);
        final Future<Integer> checkFuture = parentIds.isEmpty()?Future.succeededFuture(0):count(creator,application, search);
        return checkFuture.compose(e->{
           if(e < parentIds.size()){
               return Future.failedFuture("folder.create.parent.invalid");
           }
           //force application
           for(final JsonObject folder : folders){
               folder.put("application", application);
           }
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
        final Set<String> parentIds = Arrays.asList(folder).stream().filter(e->e.getValue("parentId") !=null).filter(e->{
            final String parentId = e.getValue("parentId").toString();
            return !(ROOT_FOLDER_ID.equalsIgnoreCase(parentId));
        }).map(e->{
            return (e.getValue("parentId").toString());
        }).collect(Collectors.toSet());
        final SearchOperation search = new SearchOperation().setIds(parentIds).setSearchEverywhere(true);
        final Future<Integer> checkFuture = parentIds.isEmpty()?Future.succeededFuture(0):count(creator,application,search);
        return checkFuture.compose(ee->{
            if(ee < parentIds.size()){
                return Future.failedFuture("folder.create.parent.invalid");
            }
            return plugin.update(creator, id, folder).map(e->{
                folder.put("_id", id);
                folder.put("updatedAt", new Date().getTime());
                return folder;
            });
        });
    }

    @Override
    public Future<List<String>> delete(final UserInfos creator, final String application, final Set<String> ids) {
        if(ids.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final List<String> idList = new ArrayList<>(ids);
        final SearchOperation search = new SearchOperation().setIds(ids).setSearchEverywhere(true);
        final Future<Integer> checkFuture = ids.isEmpty()?Future.succeededFuture(0):count(creator,application,search);
        return checkFuture.compose(ee-> {
            if (ee < ids.size()) {
                return Future.failedFuture("folder.delete.id.invalid");
            }
            return plugin.delete(creator, idList).map(idList);
        });
    }

    @Override
    public Future<List<JsonObject>> move(final UserInfos creator, final Set<String> id, final String application, final Optional<String> dest) {
        if(id.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final SearchOperation search = new SearchOperation().setIds(id).setSearchEverywhere(true);
        final Future<Integer> checkFuture = id.isEmpty()?Future.succeededFuture(0):count(creator,application,search);
        return checkFuture.compose(ee-> {
            if (ee < id.size()) {
                return Future.failedFuture("folder.move.id.invalid");
            }
            return plugin.move(creator, id, dest).compose(e -> {
                return plugin.get(creator, id).map(found -> {
                    return found;
                });
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
