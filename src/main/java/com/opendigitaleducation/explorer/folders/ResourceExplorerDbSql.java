package com.opendigitaleducation.explorer.folders;

import io.vertx.core.CompositeFuture;
import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.commons.lang3.StringUtils;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.postgres.IPostgresTransaction;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;

public class ResourceExplorerDbSql {
    private static final Logger log = LoggerFactory.getLogger(ResourceExplorerDbSql.class);
    final IPostgresClient client;
    public ResourceExplorerDbSql(final IPostgresClient client) {
        this.client = client;
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
        return client.preparedQuery(query, tuple).map(rows->{
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
        return client.preparedQuery(query, tuple).map(rows-> resourcesToMap(resources, rows));
    }

    public Future<List<ResouceSql>> muteResources(String userId, Set<String> resourceToMuteEntIds, boolean mute) {
        if(resourceToMuteEntIds.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        HashMap<String, Boolean> mutedBy = new HashMap<>();
        mutedBy.put(userId, mute);
        //must do update to return
        final List<JsonObject> resourcesColl = resourceToMuteEntIds.stream().map(entId -> {
            final JsonObject params = new JsonObject()
                    .put("ent_id", entId)
                    .put("muted_by", mutedBy);
            return params;
        }).collect(Collectors.toList());
        final List<Future> futureUpdates = new ArrayList<>();
        for (JsonObject resource : resourcesColl) {
            final Tuple values = Tuple.tuple()
                    .addValue(resource.getJsonObject("muted_by"))
                    .addValue(resource.getString("ent_id"));
            futureUpdates.add(client.preparedQuery(UPDATE_MUTED_BY, values));
        }
        final Promise<List<ResouceSql>> promiseResult = Promise.promise();
        CompositeFuture.join(futureUpdates).onComplete(e -> {
            if(e.succeeded()) {
                final List<ResouceSql> resources = new ArrayList<>();
                final List<RowSet<Row>> rowsOfRows = e.result().list();
                for (final RowSet<Row> rows : rowsOfRows) {
                    for (Row row : rows) {
                        resources.add(toResource(row));
                    }
                }
                promiseResult.complete(resources);
            } else {
                log.error("Could not execute mute query in db", e.cause());
                promiseResult.fail(e.cause());
            }
        });
        return promiseResult.future();
    }

    private ResouceSql toResource(final Row row) {
        final Integer id = row.getInteger("resource_id");
        final String entId = row.getString("ent_id");
        final String userId = row.getString("user_id");
        final String creatorId = row.getString("creator_id");
        final String resourceUniqueId = row.getString("resource_unique_id");
        final String application = row.getString("application");
        final String resource_type = row.getString("resource_type");
        final Object shared = row.getJson("shared");
        final Object mutedBy = row.getJson("muted_by");
        final long version = row.getLong("version");
        final Integer folderId = row.getInteger("folder_id");
        final ResouceSql resource = new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version);
        if(folderId != null){
            resource.folders.add(new FolderSql(folderId, userId));
        }
        if(shared != null) {
            if (shared instanceof JsonArray) {
                resource.shared.addAll((JsonArray) shared);
            }
        }
        if(mutedBy instanceof JsonObject) {
            resource.mutedBy.mergeIn((JsonObject) mutedBy);
        }
        return resource;
    }

    public Future<List<ResouceSql>> upsertResources(final Collection<? extends ExplorerMessage> resources){
        if(resources.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        //must do update to return
        final List<JsonObject> resourcesList = resources.stream().map(e->{
            final String resourceUniqueId = e.getResourceUniqueId();
            final JsonObject params = new JsonObject()
                    .put("ent_id", e.getId())
                    .put("application",e.getApplication())
                    .put("resource_type", e.getResourceType())
                    .put("resource_unique_id", resourceUniqueId)
                    .put("creator_id", e.getCreatorId())
                    .put("version", e.getVersion());
            // TODO JBER check if this is not a problem when 2 share messages are executed in the same batch
            // because there is a single upsert per resource so if the messages are presented in a reverse chronological
            // order there could be some loss
            if(e.getShared() != null &&  !e.getShared().isEmpty()){
                params.put("shared", e.getShared());
            }
            if(e.getMute() != null && !e.getMute().isEmpty()) {
                params.put("muted_by", e.getMute());
            } else {
                params.put("muted_by", new HashMap<>());
            }
            if(e.getRights() != null &&  !e.getRights().isEmpty()){
                params.put("rights", e.getRights());
            }
            return params;
        }).collect(Collectors.toList());
        //(only one upsert per resource_uniq_id)
         final Map<String, JsonObject> resourcesMap = new HashMap<>();
        for(final JsonObject json : resourcesList){
            resourcesMap.put(json.getString("resource_unique_id"), json);
        }
        final Map<String, Object> defaultVal = new HashMap<>();
        defaultVal.put("name", "");
        final Collection<JsonObject> resourcesColl = resourcesMap.values();
        final Tuple tuple = PostgresClient.insertValues(resourcesColl, Tuple.tuple(), defaultVal, "ent_id", "name", "application","resource_type","resource_unique_id", "creator_id", "version", "shared", "muted_by", "rights");
        final String insertPlaceholder = PostgresClient.insertPlaceholders(resourcesColl, 1, "ent_id", "name", "application","resource_type", "resource_unique_id", "creator_id", "version", "shared", "muted_by", "rights");
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("WITH upserted AS ( ");
        queryTpl.append("  INSERT INTO explorer.resources as r (ent_id, name,application,resource_type, resource_unique_id, creator_id, version, shared, muted_by, rights) ");
        queryTpl.append("  VALUES %s ON CONFLICT(resource_unique_id) DO UPDATE SET name=EXCLUDED.name, version=EXCLUDED.version, creator_id=COALESCE(NULLIF(EXCLUDED.creator_id,''), NULLIF(r.creator_id, ''), ''), shared=COALESCE(EXCLUDED.shared, r.shared, '[]'), muted_by=EXCLUDED.muted_by, rights=COALESCE(EXCLUDED.rights, r.rights, '[]') RETURNING * ");
        queryTpl.append(")  ");
        queryTpl.append("SELECT upserted.id as resource_id,upserted.ent_id,upserted.resource_unique_id, ");
        queryTpl.append("       upserted.creator_id, upserted.version, upserted.application, upserted.resource_type, upserted.shared, upserted.muted_by, upserted.rights, ");
        queryTpl.append("       fr.folder_id as folder_id, fr.user_id as user_id, f.trashed as folder_trash ");
        queryTpl.append("FROM upserted ");
        queryTpl.append("LEFT JOIN explorer.folder_resources fr ON upserted.id=fr.resource_id ");
        queryTpl.append("LEFT JOIN explorer.folders f ON fr.folder_id=f.id ");
        final String query = String.format(queryTpl.toString(), insertPlaceholder);
        return client.preparedQuery(query, tuple).map(rows->{
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
                final Boolean folder_trash = row.getBoolean("folder_trash");
                final Object shared = row.getJson("shared");
                final Object mutedBy = row.getJson("muted_by");
                final long version = row.getLong("version");
                final Object rights = row.getJson("rights");
                results.putIfAbsent(id, new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version));
                final ResouceSql resource = results.get(id);
                if(folderId != null){
                    if(ExplorerConfig.getInstance().isSkipIndexOfTrashedFolders()){
                        //do not link resource to folder if trashed
                        if(!Boolean.TRUE.equals(folder_trash)){
                            resource.folders.add(new FolderSql(folderId, userId));
                        }
                    }else{
                        resource.folders.add(new FolderSql(folderId, userId));
                    }
                }
                if(shared != null){
                    if(shared instanceof JsonArray){
                        resource.shared.addAll((JsonArray) shared);
                    }
                }
                if(mutedBy instanceof JsonObject) {
                    resource.mutedBy.mergeIn((JsonObject) mutedBy);
                }
                if(rights != null){
                    if(rights instanceof JsonArray){
                        results.get(id).rights.addAll((JsonArray) rights);
                    }
                }
                if(mutedBy instanceof JsonObject) {
                    resource.mutedBy.mergeIn((JsonObject) mutedBy);
                }
            }
            return new ArrayList<>(results.values());
        });
    }

    /**
     * @param resourceId Unique ID of the resource whose muters id we want
     * @return Id of the users who muted this resource
     */
    public Future<Set<String>> getMutedByEntId(final String resourceId) {
        final Future<Set<String>> mutedByFuture;
        if (StringUtils.isBlank(resourceId)) {
            mutedByFuture = Future.succeededFuture(emptySet());
        } else {
            final String queryTpl = "SELECT muted_by FROM explorer.resources WHERE ent_id = $1";
            final Tuple tuple = Tuple.tuple().addString(resourceId);
            mutedByFuture = client.preparedQuery(queryTpl, tuple).map(rows -> {
                final Set<String> mutedBy = new HashSet<>();
                for(final Row row : rows) {
                    final Object mutedByModel = row.getJson("muted_by");
                    if (mutedByModel instanceof JsonObject) {
                        mutedBy.addAll(((JsonObject) mutedByModel).stream()
                                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toSet()));
                    }
                }
                return  mutedBy;
            });
        }
        return mutedByFuture;
    }

    public Future<Set<ResouceSql>> getSharedByEntIds(final Set<String> ids) {
        if (ids.isEmpty()) {
            return Future.succeededFuture(new HashSet<>());
        }
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final String queryTpl = "SELECT * FROM explorer.resources WHERE ent_id IN (%s)";
        final String query = String.format(queryTpl, inPlaceholder);
        final Tuple tuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        return client.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final Object shared = row.getJson("shared");
                final long version = row.getLong("version");
                final ResouceSql res = new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version);
                if(shared != null){
                    if(shared instanceof JsonArray){
                        res.shared.addAll((JsonArray) shared);
                    }
                }
                resources.add(res);
            }
            return  resources;
        });
    }

    public Future<Set<ResouceSql>> getModelByIds(final Set<Integer> ids){
        if(ids.isEmpty()){
            return Future.succeededFuture(new HashSet<>());
        }
        final Tuple tuple = Tuple.tuple();
        PostgresClient.inTuple(tuple, ids);
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final String queryTpl = "SELECT * FROM explorer.resources WHERE id IN (%s)";
        final String query = String.format(queryTpl, inPlaceholder);
        return client.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final long version = row.getLong("version");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version));
            }
            return  resources;
        });
    }

    public Future<List<ResourceId>> getIdsByFolderIds(final Set<Integer> ids){
        if(ids.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final Tuple tuple = Tuple.tuple();
        PostgresClient.inTuple(tuple, ids);
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final String queryTpl = "SELECT resource_id, r.ent_id as ent_id FROM explorer.folder_resources INNER JOIN explorer.resources r ON r.id = resource_id WHERE folder_id IN (%s)";
        final String query = String.format(queryTpl, inPlaceholder);
        return client.preparedQuery(query, tuple).map(rows ->{
            final List<ResourceId> resources = new ArrayList<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("resource_id");
                final String ent_id = row.getString("ent_id");
                resources.add(new ResourceId(id, ent_id));
            }
            return  resources;
        });
    }

    public Future<Set<ResouceSql>> getModelByEntIds(final Set<String> ids){
        if(ids.isEmpty()){
            return Future.succeededFuture(new HashSet<>());
        }
        final Tuple tuple = Tuple.tuple();
        PostgresClient.inTuple(tuple, ids);
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final String queryTpl = "SELECT * FROM explorer.resources WHERE ent_id IN (%s)";
        final String query = String.format(queryTpl, inPlaceholder);
        return client.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final long version = row.getLong("version");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version));
            }
            return  resources;
        });
    }

    public Future<Set<ResouceSql>> updateShareById(final Set<Integer> ids, final JsonArray shared){
        if(ids.isEmpty()){
            return Future.succeededFuture(new HashSet<>());
        }
        final Tuple tuple = Tuple.tuple().addValue((shared));
        PostgresClient.inTuple(tuple, ids);
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 2);
        final String queryTpl = "UPDATE explorer.resources SET shared = $1 WHERE id IN (%s) RETURNING * ";
        final String query = String.format(queryTpl, inPlaceholder);
        return client.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final long version = row.getLong("version");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version));
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
        return client.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final long version = row.getLong("version");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version));
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
        return client.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){ ;
                final Integer id = row.getInteger("id");
                final String entId = row.getString("ent_id");
                final String creatorId = row.getString("creator_id");
                final String resourceUniqueId = row.getString("resource_unique_id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final long version = row.getLong("version");
                resources.add(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version));
            }
            return  resources;
        });
    }
    public Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trash(final Collection<Integer> resourceIds, final boolean trashed) {
        return client.transaction().compose(transaction->{
           final Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> future = this.trash(transaction, resourceIds, trashed);
           return transaction.commit().compose(commit -> future);
        });
    }

    public Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trash(final IPostgresTransaction transaction, final Collection<Integer> resourceIds, final boolean trashed){
        if(resourceIds.isEmpty()){
            return Future.succeededFuture(new HashMap<>());
        }
        final Map<Integer, FolderExplorerDbSql.FolderTrashResult> mapTrashed = new HashMap<>();
        final Tuple tuple = PostgresClient.inTuple(Tuple.of(trashed), resourceIds);
        final String inPlaceholder = PostgresClient.inPlaceholder(resourceIds, 2);
        final String query = String.format("UPDATE explorer.resources SET trashed=$1 WHERE id IN (%s) RETURNING *", inPlaceholder);
        final Future<RowSet<Row>> future = transaction.addPreparedQuery(query, tuple).onSuccess(rows->{
            for(final Row row : rows){
                final Integer id = row.getInteger("id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final String ent_id = row.getString("ent_id");
                final Optional<Integer> parentOpt = Optional.empty();
                mapTrashed.put(id, new FolderExplorerDbSql.FolderTrashResult(id, parentOpt, application, resource_type, ent_id));
            }
        });
        return future.map(mapTrashed);
    }

    private Map<Integer, ExplorerMessage> resourcesToMap(final Collection<? extends ExplorerMessage> resources, final RowSet<Row> rows){
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

        public ResouceSql(String entId, Integer id, String resourceUniqId, String creatorId, String application, String resourceType, final long version) {
            this.entId = entId;
            this.id = id;
            this.creatorId = creatorId;
            this.resourceUniqId = resourceUniqId;
            this.application = application;
            this.resourceType = resourceType;
            this.version = version;
        }
        public final String creatorId;
        //list of all folders
        public final List<FolderSql> folders = new ArrayList<>();
        public final JsonArray shared = new JsonArray();
        public final JsonObject mutedBy = new JsonObject();
        public final JsonArray rights = new JsonArray();
        public final String entId;
        public final Integer id;
        public final String resourceUniqId;
        public final String resourceType;
        public final String application;
        public final long version;

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

        public Map<String, Set<String>> getRightsByUser(){
            final Map<String, Set<String>> all = new HashMap<>();
            for(final Object row : shared){
                if(row instanceof JsonObject){
                    final JsonObject jsonRow = (JsonObject) row;
                    final String id = jsonRow.getString("userId");
                    if(id!=null){
                        final Set<String> rights = new HashSet<>(jsonRow.fieldNames());
                        rights.remove("userId");
                        all.put(id, rights);
                    }
                }
            }
            return all;
        }

        public Map<String, Set<String>> getRightsForGroup(){
            final Map<String, Set<String>> all = new HashMap<>();
            for(final Object row : shared){
                if(row instanceof JsonObject){
                    final JsonObject jsonRow = (JsonObject) row;
                    final String id = jsonRow.getString("groupId");
                    if(id!=null){
                        final Set<String> rights = new HashSet<>(jsonRow.fieldNames());
                        rights.remove("groupId");
                        all.put(id, rights);
                    }
                }
            }
            return all;
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
    public static class ResourceId{

        public ResourceId(Integer id, String entId) {
            this.id = id;
            this.entId = entId;
        }
        public final Integer id;
        public final String entId;
    }

    private static final String UPSERT_RESOURCE_QUERY = "WITH upserted AS ( " +
            "  INSERT INTO explorer.resources as r (ent_id, name,application,resource_type, resource_unique_id, creator_id, version, shared, muted_by) " +
            "  VALUES %s ON CONFLICT(resource_unique_id) DO UPDATE SET name=EXCLUDED.name, version=EXCLUDED.version, shared=COALESCE(EXCLUDED.shared, COALESCE(r.shared, '[]')), muted_by=EXCLUDED.muted_by || r.muted_by RETURNING * " +
            ")  " +
            "SELECT upserted.id as resource_id,ent_id,resource_unique_id, creator_id, version, " +
            "       application, resource_type, shared, " +
            "       fr.folder_id as folder_id, fr.user_id as user_id, muted_by " +
            "FROM upserted " +
            "LEFT JOIN explorer.folder_resources fr ON upserted.id=fr.resource_id ";

    private static final String UPDATE_MUTED_BY = "WITH updated AS (UPDATE explorer.resources " +
            "SET muted_by = muted_by || $1 " +
            "WHERE ent_id = $2 RETURNING *) " +
            "SELECT updated.id as resource_id,ent_id,resource_unique_id, creator_id, version, " +
            "       application, resource_type, shared, " +
            "       fr.folder_id as folder_id, fr.user_id as user_id, muted_by " +
            "FROM updated " +
            "LEFT JOIN explorer.folder_resources fr ON updated.id=fr.resource_id ";
}
