package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.plugin.ExplorerResourceCrudSql;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientPool;
import io.reactiverse.pgclient.PgRowSet;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.reactiverse.pgclient.data.Json;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class ResourceExplorerCrudSql  {
    final PostgresClientPool pgPool;
    public ResourceExplorerCrudSql(final PostgresClient pool) {
        this.pgPool = pool.getClientPool();
    }

    public Future<Map<Integer, ExplorerMessage>> deleteTemporarlyResources(final Collection<? extends ExplorerMessage> resources){
        if(resources.isEmpty()){
            return Future.succeededFuture(new HashMap<>());
        }
        final Set<String> uniqIds = resources.stream().map(e->e.getResourceUniqueId()).collect(Collectors.toSet());
        final Tuple tuple = PostgresClient.inTuple(Tuple.tuple(), uniqIds);
        final String inPlaceholder = PostgresClient.inPlaceholder(uniqIds, 1);
        final String queryTpl = "UPDATE explorer.resources SET deleted=TRUE WHERE resource_unique_id IN (%s) RETURNING *";
        final String query = String.format(queryTpl, inPlaceholder);
        return pgPool.preparedQuery(query, tuple).map(rows->{
            return resourcesToMap(resources, rows);
        });
    }

    public Future<Map<Integer, ExplorerMessage>> deleteDefinitlyResources(final Collection<? extends ExplorerMessage> resources){
        if(resources.isEmpty()){
            return Future.succeededFuture(new HashMap<>());
        }
        final Set<String> uniqIds = resources.stream().map(e->e.getResourceUniqueId()).collect(Collectors.toSet());
        final Tuple tuple = PostgresClient.inTuple(Tuple.tuple(), uniqIds);
        final String inPlaceholder = PostgresClient.inPlaceholder(uniqIds, 1);
        final String queryTpl = "DELETE FROM explorer.resources WHERE resource_unique_id IN (%s) AND deleted IS TRUE RETURNING *";
        final String query = String.format(queryTpl, inPlaceholder);
        return pgPool.preparedQuery(query, tuple).map(rows->{
            return resourcesToMap(resources, rows);
        });
    }

    public Future<List<ResouceSql>> upsertResources(final Collection<? extends ExplorerMessage> resources){
        if(resources.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        //must do update to return
        final List<JsonObject> resourcesList = resources.stream().map(e->{
            final String resourceUniqueId = e.getResourceUniqueId();
            return new JsonObject().put("ent_id", e.getId()).put("application",e.getApplication())
                    .put("resource_type", e.getResourceType()).put("resource_unique_id", resourceUniqueId)
                    .put("creator_id", e.getCreatorId());
        }).collect(Collectors.toList());
        //(only one upsert per resource_uniq_id)
        final Map<String, JsonObject> resourcesMap = new HashMap<>();
        for(final JsonObject json : resourcesList){
            resourcesMap.put(json.getString("resource_unique_id"), json);
        }
        final Map<String, Object> defaultVal = new HashMap<>();
        defaultVal.put("name", "");
        final Collection<JsonObject> resourcesColl = resourcesMap.values();
        final Tuple tuple = PostgresClient.insertValues(resourcesColl, Tuple.tuple(), defaultVal, "ent_id", "name","application","resource_type","resource_unique_id", "creator_id");
        final String insertPlaceholder = PostgresClient.insertPlaceholders(resourcesColl, 1, "ent_id", "name","application","resource_type", "resource_unique_id", "creator_id");
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("WITH upserted AS ( ");
        queryTpl.append("  INSERT INTO explorer.resources as r (ent_id, name,application,resource_type, resource_unique_id, creator_id) ");
        queryTpl.append("  VALUES %s ON CONFLICT(resource_unique_id) DO UPDATE SET name=r.name RETURNING * ");
        queryTpl.append(")  ");
        queryTpl.append("SELECT upserted.id as resource_id,ent_id,resource_unique_id, creator_id, ");
        queryTpl.append("       application, resource_type, shared, ");
        queryTpl.append("       fr.folder_id as folder_id, fr.user_id as user_id ");
        queryTpl.append("FROM upserted ");
        queryTpl.append("LEFT JOIN explorer.folder_resources fr ON upserted.id=fr.resource_id ");
        final String query = String.format(queryTpl.toString(), insertPlaceholder);
        return pgPool.preparedQuery(query, tuple).map(rows->{
            final Map<Integer, ResouceSql> results = new HashMap<>();
            for(final Row row : rows){
                final Integer id = row.getInteger("resource_id");
                final String entId = row.getString("ent_id");
                final String userId = row.getString("user_id");
                final Integer folderId = row.getInteger("folder_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final Json shared = row.getJson("shared");
                results.putIfAbsent(id, new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type));
                if(folderId != null){
                    results.get(id).folders.add(new FolderSql(folderId, userId));
                }
                if(shared != null){
                    final Object value = shared.value();
                    if(value instanceof JsonArray){
                        results.get(id).shared.addAll((JsonArray) value);
                    }
                }
            }
            return new ArrayList<>(results.values());
        });
    }

    public Future<Set<ResouceSql>> updateShareById(final Set<Integer> ids, final JsonArray shared){
        if(ids.isEmpty()){
            return Future.succeededFuture(new HashSet<>());
        }
        final Tuple tuple = Tuple.tuple().addJson(Json.create(shared));
        PostgresClient.inTuple(tuple, ids);
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 2);
        final String queryTpl = "UPDATE explorer.resources SET shared = $1 WHERE id IN (%s) RETURNING * ";
        final String query = String.format(queryTpl, inPlaceholder);
        return pgPool.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type));
            }
            return  resources;
        });
    }

    public Future<Set<ResouceSql>> moveTo(final Set<Integer> ids, final Integer dest, final UserInfos user){
        if(ids.isEmpty()){
            return Future.succeededFuture(new HashSet<>());
        }
        final List<JsonObject> jsons = ids.stream().map(e-> {
            return new JsonObject().put("resource_id", e).put("folder_id", dest).put("user_id", user.getUserId());
        }).collect(Collectors.toList());
        final Tuple tuple = PostgresClient.insertValues(jsons, Tuple.tuple(),"folder_id", "resource_id", "user_id");
        final String insertPlaceholder = PostgresClient.insertPlaceholders(jsons, 1,"folder_id", "resource_id", "user_id");
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("WITH updated AS ( ");
        queryTpl.append("   INSERT INTO explorer.folder_resources(folder_id, resource_id, user_id) VALUES %s ");
        queryTpl.append("   ON CONFLICT(resource_id, user_id) DO UPDATE SET folder_id=EXCLUDED.folder_id RETURNING * ");
        queryTpl.append(") SELECT * FROM explorer.resources WHERE id IN (SELECT resource_id FROM updated) ");
        final String query = String.format(queryTpl.toString(), insertPlaceholder);
        return pgPool.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type));
            }
            return  resources;
        });
    }


    public Future<Set<ResouceSql>> moveToRoot(final Set<Integer> ids, final UserInfos user){
        if(ids.isEmpty()){
            return Future.succeededFuture(new HashSet<>());
        }
        final Tuple tuple = PostgresClient.inTuple( Tuple.tuple(),ids);
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("WITH deleted AS ( ");
        queryTpl.append("   DELETE FROM explorer.folder_resources WHERE resource_id IN (%s) RETURNING * ");
        queryTpl.append(") SELECT * FROM explorer.resources WHERE id IN (SELECT resource_id FROM deleted) ");
        final String query = String.format(queryTpl.toString(), inPlaceholder);
        return pgPool.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type));
            }
            return  resources;
        });
    }

    private Map<Integer, ExplorerMessage> resourcesToMap(final Collection<? extends ExplorerMessage> resources, final PgRowSet rows){
        final Map<Integer,ExplorerMessage> newIds = new HashMap<>();
        for(final Row row : rows){
            final String resourceUniqueId = row.getString("resource_unique_id");
            final Optional<? extends ExplorerMessage> first = resources.stream().filter(e->e.getResourceUniqueId().equals(resourceUniqueId)).findFirst();
            if(first.isPresent()){
                newIds.put(row.getInteger("id"), first.get());
            }
        }
        return newIds;
    }

    public static class ResouceSql{

        public ResouceSql(String entId, Integer id, String resourceUniqId, String creatorId, String application, String resourceType) {
            this.entId = entId;
            this.id = id;
            this.creatorId = creatorId;
            this.resourceUniqId = resourceUniqId;
            this.application = application;
            this.resourceType = resourceType;
        }
        public final String creatorId;
        //list of all folders
        public final List<FolderSql> folders = new ArrayList<>();
        public final JsonArray shared = new JsonArray();
        public final String entId;
        public final Integer id;
        public final String resourceUniqId;
        public final String resourceType;
        public final String application;

        public Set<String> getSharedUsers(){
            final Set<String> ids = new HashSet<>();
            for(final Object row : shared){
                if(row instanceof JsonObject){
                    final JsonObject jsonRow = (JsonObject) row;
                    final String id = jsonRow.getString("userId");
                    if(id!=null){
                        ids.add(id);
                    }
                }
            }
            return ids;
        }
        public Set<String> getSharedGroups(){
            final Set<String> ids = new HashSet<>();
            for(final Object row : shared){
                if(row instanceof JsonObject){
                    final JsonObject jsonRow = (JsonObject) row;
                    final String id = jsonRow.getString("groupId");
                    if(id!=null){
                        ids.add(id);
                    }
                }
            }
            return ids;
        }
    }
    public static class FolderSql{

        public FolderSql(Integer id, String userId) {
            this.id = id;
            this.userId = userId;
        }
        public final Integer id;
        public final String userId;
    }
}