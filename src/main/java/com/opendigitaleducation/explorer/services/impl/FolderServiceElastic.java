package com.opendigitaleducation.explorer.services.impl;
import static com.opendigitaleducation.explorer.ExplorerConfig.ROOT_FOLDER_ID;

import com.opendigitaleducation.explorer.ExplorerConfig;
import org.entcore.common.elasticsearch.ElasticClient;
import org.entcore.common.elasticsearch.ElasticClientManager;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.ingest.MessageIngesterElastic;
import com.opendigitaleducation.explorer.services.FolderService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;


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
    public Future<JsonArray> fetch(final UserInfos creator, final Optional<String> parentIdOpt) {
        final String parentId = parentIdOpt.orElse(ROOT_FOLDER_ID);
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId).withParentId(parentId);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        final String index = getIndex();
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos creator, final SearchOperation search) {
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId);
        if (search.getParentId().isPresent()) {
            query.withFolderId(search.getParentId().get());
        } else if (!search.isSearchEverywhere()) {
            query.withOnlyRoot(true);
        }
        if (search.getTrashed() != null) {
            query.withTrashed(search.getTrashed());
        }
        if(search.getId() != null){
            query.withId(search.getId());
        }
        final String index = getIndex();
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    @Override
    public Future<Integer> count(UserInfos creator, SearchOperation search) {
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId);
        if (search.getParentId().isPresent()) {
            query.withFolderId(search.getParentId().get());
        } else if (!search.isSearchEverywhere()) {
            query.withOnlyRoot(true);
        }
        if (search.getTrashed() != null) {
            query.withTrashed(search.getTrashed());
        }
        if(search.getId() != null){
            query.withId(search.getId());
        }
        final String index = getIndex();
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        return manager.getClient().count(index, query.getSearchQuery(), options);
    }

    protected String getRoutingKey(final UserInfos creator) {
        //TODO use application?
        return creator.getUserId();
    }

    @Override
    public Future<String> create(final UserInfos creator, final JsonObject folder) {
        return plugin.create(creator, folder, false).map(id ->{
            folder.put("_id", id);
            return id;
        });
    }

    @Override
    public Future<List<JsonObject>> create(final UserInfos creator, final List<JsonObject> folders) {
        if(folders.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        return plugin.create(creator, folders, false).map(ids ->{
            for(int i = 0; i < folders.size(); i++){
                folders.get(i).put("_id", ids.get(i));
            }
            return folders;
        });
    }

    @Override
    public Future<JsonObject> update(final UserInfos creator, final String id, final JsonObject folder) {
        return plugin.update(creator, id, folder).map(e->{
            folder.put("_id", id);
            folder.put("updatedAt", new Date().getTime());
            return folder;
        });
    }

    @Override
    public Future<List<String>> delete(final UserInfos creator, final Set<String> ids) {
        if(ids.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final List<String> idList = new ArrayList<>(ids);
        return plugin.delete(creator, idList).map(idList);
    }

    @Override
    public Future<JsonObject> move(final UserInfos creator, final String id, final Optional<String> dest) {
        return plugin.move(creator, id, dest).compose(e->{
            return plugin.get(creator, id).compose(found->{
                if(found.isPresent()){
                    return Future.succeededFuture(found.get());
                }else{
                    return Future.failedFuture("folder.notfound");
                }
            });
        });
    }
}
