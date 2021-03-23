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

import java.util.*;
import java.util.stream.Collectors;


public class FolderServiceElastic implements FolderService {
    final ElasticClientManager manager;
    final String index;
    final boolean waitFor = true;

    public FolderServiceElastic(final ElasticClientManager aManager) {
        this(aManager, DEFAULT_FOLDER_INDEX);
    }

    public FolderServiceElastic(final ElasticClientManager aManager, final String index) {
        this.manager = aManager;
        this.index = index;
    }

    @Override
    public Future<JsonArray> fetch(final UserInfos creator, final Optional<String> parentIdOpt) {
        final String parentId = parentIdOpt.orElse(ROOT_FOLDER_ID);
        final String creatorId = creator.getUserId();
        final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId).withParentId(parentId);
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
        return manager.getClient().search(index, query.getSearchQuery(), options);
    }

    protected String getRoutingKey(final UserInfos creator) {
        //TODO use application?
        return creator.getUserId();
    }

    @Override
    public Future<String> create(final UserInfos creator, final JsonObject folder) {
        return beforeCreate(creator, Arrays.asList(folder)).compose(prepare -> {
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(getRoutingKey(creator));
            return manager.getClient().createDocument(index, folder, options);
        }).compose(e -> {
            folder.put("_id", e);
            return afterCreate(creator, Arrays.asList(folder)).map(e);
        });
    }

    protected Future<Void> afterCreate(final UserInfos creator, final List<JsonObject> folders) {
        //set children ids
        final Map<String, Set<String>> childrenMap = new HashMap<>();
        for (final JsonObject folder : folders) {
            final String id = folder.getString("_id");
            final String parentId = folder.getString("parentId");
            if (parentId != null && !ROOT_FOLDER_ID.equals(parentId)) {
                //TODO UPDATE ROOT parent only if need to load all at root (generateRootId)
                childrenMap.putIfAbsent(parentId, new HashSet<>());
                childrenMap.get(parentId).add(id);
            }
        }
        //if empty
        if (childrenMap.isEmpty()) {
            return Future.succeededFuture();
        }
        //bulk
        final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(getRoutingKey(creator));
        final ElasticBulkRequest bulk = manager.getClient().bulk(index, options);
        for (final Map.Entry<String, Set<String>> entry : childrenMap.entrySet()) {
            final JsonArray childrenIds = new JsonArray(new ArrayList(entry.getValue()));
            final JsonObject params = new JsonObject().put("children", childrenIds);
            final JsonObject script = new JsonObject().put("lang", "painless").put("params", params);
            final JsonObject payload = new JsonObject().put("script", script);
            payload.put("upsert", new JsonObject().put("childrenIds", childrenIds));
            script.put("source", "ctx._source.childrenIds.removeAll(params.children);ctx._source.childrenIds.addAll(params.children);");
            bulk.script(payload, entry.getKey());
        }
        return bulk.end().mapEmpty();
    }

    @Override
    public Future<List<JsonObject>> create(final UserInfos creator, final List<JsonObject> folders) {
        return beforeCreate(creator, folders).compose(prepare -> {
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withWaitFor(waitFor).withRouting(getRoutingKey(creator));
            final ElasticBulkRequest bulk = manager.getClient().bulk(index, options);
            for (final JsonObject folder : folders) {
                bulk.create(folder);
            }
            //update counter
            return bulk.end().compose(results -> {
                boolean success = true;
                String message = "";
                for (int i = 0; i < folders.size(); i++) {
                    final ElasticBulkRequest.ElasticBulkRequestResult res = results.get(i);
                    final JsonObject folder = folders.get(i);
                    if (res.isOk()) {
                        folder.put("_id", res.getId());
                        folder.put(SUCCESS_FIELD, true);
                    } else {
                        success = false;
                        folder.put(ERROR_FIELD, res.getMessage());
                        folder.put(SUCCESS_FIELD, false);
                        message += " - " + res.getMessage();
                    }
                }
                if (success) {
                    return Future.succeededFuture(folders);
                } else {
                    return Future.failedFuture(message);
                }
            });
        }).compose(e -> {
            //TODO avoid wait for if multiple create query?
            //TODO if predictible id merge request in bulk?
            return afterCreate(creator, folders).map(e);
        });
    }

    protected boolean hasParent(final JsonObject folder) {
        final String parentId = folder.getString("parentId");
        return hasParent(parentId);
    }


    protected boolean hasParent(final String parentId) {
        return !ROOT_FOLDER_ID.equals(parentId) && !StringUtils.isEmpty(parentId);
    }

    protected Future<Void> mergeAncestors(final UserInfos creator, final List<JsonObject> folders) {
        final List<String> ids = folders.stream().map(f -> f.getString("parentId")).filter(f -> hasParent(f)).collect(Collectors.toList());
        if (ids.isEmpty()) {
            for (final JsonObject folder : folders) {
                folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
                folder.put("parentId", ROOT_FOLDER_ID);
            }
            return Future.succeededFuture();
        } else {
            final String creatorId = creator.getUserId();
            final FolderQueryElastic query = new FolderQueryElastic().withCreatorId(creatorId).withId(ids).withFrom(0).withSize(ids.size());
            final ElasticClient.ElasticOptions options = new ElasticClient.ElasticOptions().withRouting(getRoutingKey(creator));
            return manager.getClient().search(index, query.getSearchQuery(), options).compose(parents -> {
                for (final JsonObject folder : folders) {
                    final String parentId = folder.getString("parentId");
                    if (hasParent(parentId)) {
                        final Optional<JsonObject> found = parents.stream().map(o -> (JsonObject) o).filter(p -> parentId.equals(p.getString("_id"))).findFirst();
                        if (found.isPresent()) {
                            final JsonArray ancestors = found.get().getJsonArray("ancestors", new JsonArray()).add(parentId);
                            folder.put("ancestors", ancestors);
                        } else {
                            folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
                            folder.put("parentId", ROOT_FOLDER_ID);
                        }
                    } else {
                        folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
                        folder.put("parentId", ROOT_FOLDER_ID);
                    }
                }
                return Future.succeededFuture();
            });
        }
    }

    protected Future<Void> beforeCreate(final UserInfos creator, final List<JsonObject> folders) {
        for (final JsonObject folder : folders) {
            folder.put("creatorId", creator.getUserId());
            folder.put("creatorName", creator.getUsername());
            folder.put("createdAt", new Date().getTime());
            folder.put("updatedAt", new Date().getTime());
            if (!folder.containsKey("parentId")) {
                folder.put("parentId", ROOT_FOLDER_ID);
            }
            if (!folder.containsKey("ancestors")) {
                folder.put("ancestors", new JsonArray().add(ROOT_FOLDER_ID));
            }
            if (!folder.containsKey("trashed")) {
                folder.put("trashed", false);
            }
            if (!folder.containsKey("childrenIds")) {
                folder.put("childrenIds", new JsonArray());
            }
        }
        return mergeAncestors(creator, folders);
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
        return client.search(this.index, query.getSearchQuery(), options).compose(fetched -> {
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
            return manager.getClient().updateByQuery(this.index, payload, options2).map(document);
        });
    }
}
