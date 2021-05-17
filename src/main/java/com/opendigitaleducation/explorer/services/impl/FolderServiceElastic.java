package com.opendigitaleducation.explorer.services.impl;
import static com.opendigitaleducation.explorer.ExplorerConfig.ROOT_FOLDER_ID;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
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
    final boolean waitFor = true;

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
    public Future<JsonObject> move(final UserInfos creator, final JsonObject document, final Optional<String> source, final Optional<String> dest) {
        final ElasticClient client = manager.getClient();
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creator.getUserId()).withId(source.orElse(null)).withId(dest.orElse(null));
        if (query.getId().isEmpty()) {
            //root to root
            return Future.succeededFuture(document);
        }
        final String index = getIndex();
        return client.search(index, query.getSearchQuery(), options).compose(fetched -> {
            final Optional<JsonArray> oldIds = fetched.stream().map(e -> (JsonObject) e).filter(e -> e.getString("_id").equals(source.orElse(null))).map(e -> e.getJsonArray("ancestors")).findFirst();
            final Optional<JsonArray> newIds = fetched.stream().map(e -> (JsonObject) e).filter(e -> e.getString("_id").equals(dest.orElse(null))).map(e -> e.getJsonArray("ancestors")).findFirst();
            //params
            final JsonObject params = new JsonObject();
            params.put("oldIds", oldIds.orElse(new JsonArray()).add(source.orElse(ROOT_FOLDER_ID)));
            params.put("newIds", newIds.orElse(new JsonArray()).add(dest.orElse(ROOT_FOLDER_ID)));
            params.put("oldParent", source.orElse(ROOT_FOLDER_ID)).put("newParent", dest.orElse(ROOT_FOLDER_ID));
            //script
            final JsonObject script = new JsonObject().put("lang", "painless").put("params", params);
            final JsonObject payload = new JsonObject().put("script", script);
            final FolderQueryElastic query2 = new FolderQueryElastic().withCreatorId(creator.getUserId()).withAncestors(source.orElse(null));
            //TODO avoid search by ancestor (if root match all) => get by id=document.id or ancestors=document.id + conditional update using script
            payload.put("query", query2.getSearchQuery().getJsonObject("query"));
            //remove all ancestor of new parent
            final StringBuilder sourceScript = new StringBuilder();
            sourceScript.append("ctx._source.ancestors.removeAll(params.oldIds);");
            sourceScript.append("ctx._source.ancestors.addAll(params.newIds);");
            sourceScript.append("if(ctx._source.parentId==params.oldParent)ctx._source.parentId=params.newParent;");
            script.put("source", sourceScript);
            final ElasticClient.ElasticOptions options2 = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator)).withRefresh(waitFor);
            return manager.getClient().updateByQuery(index, payload, options2).map(document);
        });
    }
}
