package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.elastic.ElasticBulkRequest;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.services.FolderService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class ElasticFolderService implements FolderService {
    final ElasticClientManager manager;
    final String index;
    final boolean waitFor = true;

    public ElasticFolderService(final ElasticClientManager aManager) {
        this(aManager, DEFAULT_FOLDER_INDEX);
    }

    public ElasticFolderService(final ElasticClientManager aManager, final String index) {
        this.manager = aManager;
        this.index = index;
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos creator, final Optional<String> parentIdOpt) {
        final String parentId = parentIdOpt.orElse(ROOT_FOLDER_ID);
        final String creatorId = creator.getUserId();
        final ElasticFolderQuery query = new ElasticFolderQuery().withCreatorId(creatorId).withParentId(parentId);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    protected String getRoutingKey(final UserInfos creator){
        //TODO use application?
        return creator.getUserId();
    }

    @Override
    public Future<String> create(final UserInfos creator, final JsonObject folder) {
        return beforeCreate(creator, Arrays.asList(folder)).compose(prepare->{
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(getRoutingKey(creator));
            return manager.getClient().createDocument(index, folder, options);
        });
    }

    @Override
    public Future<List<JsonObject>> create(final UserInfos creator, final List<JsonObject> folders) {
        return beforeCreate(creator, folders).compose(prepare-> {
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(getRoutingKey(creator));
            final ElasticBulkRequest bulk = manager.getClient().bulk(index, options);
            for (final JsonObject folder : folders) {
                bulk.create(folder);
            }
            return bulk.end().compose(results -> {
                boolean success = true;
                String message = "";
                for (int i = 0; i < folders.size(); i++) {
                    final ElasticBulkRequest.ElasticBulkRequestResult res = results.get(i);
                    if (res.isOk()) {
                        folders.get(i).put("_id", res.getId());
                    } else {
                        success = false;
                        folders.get(i).put("_error", res.getMessage());
                        message += " - " + res.getMessage();
                    }
                }
                if (success) {
                    return Future.succeededFuture(folders);
                } else {
                    return Future.failedFuture(message);
                }
            });
        });
    }

    protected boolean hasParent(final JsonObject folder){
        final String parentId = folder.getString("parentId");
        return hasParent(parentId);
    }


    protected boolean hasParent(final String parentId){
        return !ROOT_FOLDER_ID.equals(parentId) && !StringUtils.isEmpty(parentId);
    }

    protected Future<Void> mergeAncestors(final UserInfos creator, final List<JsonObject> folders){
        final List<String> ids = folders.stream().map(f->f.getString("parentId")).filter(f->hasParent(f)).collect(Collectors.toList());
        if(ids.isEmpty()){
            for(final JsonObject folder : folders){
                folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
                folder.put("parentId", ROOT_FOLDER_ID);
            }
            return Future.succeededFuture();
        }else{
            final String creatorId = creator.getUserId();
            final ElasticFolderQuery query = new ElasticFolderQuery().withCreatorId(creatorId).withId(ids);
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
            return manager.getClient().search(index, query.getSearchQuery(), options).compose(parents->{
                for(final JsonObject folder : folders){
                    final String parentId = folder.getString("parentId");
                    if(hasParent(parentId)){
                        final Optional<JsonObject> found = parents.stream().map(o->(JsonObject)o).filter(p->parentId.equals(p.getString("_id"))).findFirst();
                        if(found.isPresent()){
                            final JsonArray ancestors = found.get().getJsonArray("ancestors", new JsonArray()).add(parentId);
                            folder.put("ancestors", ancestors);
                        }else{
                            folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
                            folder.put("parentId", ROOT_FOLDER_ID);
                        }
                    }else{
                        folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
                        folder.put("parentId", ROOT_FOLDER_ID);
                    }
                }
                return Future.succeededFuture();
            });
        }
    }

    protected Future<Void> beforeCreate(final UserInfos creator, final List<JsonObject> folders){
        for(final JsonObject folder : folders){
            folder.put("creatorId", creator.getUserId());
            folder.put("creatorName", creator.getUsername());
            folder.put("createdAt", new Date().getTime());
            folder.put("updatedAt", new Date().getTime());
            if(!folder.containsKey("parentId")){
                folder.put("parentId", ROOT_FOLDER_ID);
            }
            if(!folder.containsKey("ancestors")){
                folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
            }
            if(!folder.containsKey("trashed")){
                folder.put("trashed", false);
            }
        }
        return mergeAncestors(creator, folders);
    }
}
