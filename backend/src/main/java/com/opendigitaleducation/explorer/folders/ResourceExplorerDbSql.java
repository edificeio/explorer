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
import org.entcore.common.explorer.IdAndVersion;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.postgres.IPostgresTransaction;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.function.Function;
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
        final Object mutedBy = row.getJson("muted_by");
        final long version = row.getLong("version");
        final Integer folderId = row.getInteger("folder_id");
        final ResouceSql resource = new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version);
        if(folderId != null){
            resource.folders.add(new FolderSql(folderId, userId));
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
                    .put("version", e.getVersion());
            // subresource upsert dont have creatorid
            if(StringUtils.isNotBlank(e.getCreatorId())){
                params.put("creator_id", e.getCreatorId());
            }
            // TODO JBER check if this is not a problem when 2 share messages are executed in the same batch
            // because there is a single upsert per resource so if the messages are presented in a reverse chronological
            // order there could be some loss
            if(e.getRights() != null &&  !e.getRights().isEmpty()){
                params.put("rights", e.getRights());
            }
            return params;
        }).collect(Collectors.toList());
        //(only one upsert per resource_uniq_id)
         final Map<String, JsonObject> resourcesMap = new HashMap<>();
        for(final JsonObject json : resourcesList){
            final String id = json.getString("resource_unique_id");
            final JsonObject params = resourcesMap.getOrDefault(id, new JsonObject());
            params.mergeIn(json);
            // if does not exists yet => creatorid should not be null
            if(params.getValue("creator_id") == null){
                params.put("creator_id", "");
            }
            resourcesMap.put(id, params);
        }
        final Map<String, Object> defaultVal = new HashMap<>();
        defaultVal.put("name", "");
        final Collection<JsonObject> resourcesColl = resourcesMap.values();
        final Tuple tuple = PostgresClient.insertValues(resourcesColl, Tuple.tuple(), defaultVal, "ent_id", "name", "application","resource_type","resource_unique_id", "creator_id", "version", "rights");
        final String insertPlaceholder = PostgresClient.insertPlaceholders(resourcesColl, 1, "ent_id", "name", "application","resource_type", "resource_unique_id", "creator_id", "version", "rights");
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("WITH upserted AS ( ");
        queryTpl.append("  INSERT INTO explorer.resources as r (ent_id, name,application,resource_type, resource_unique_id, creator_id, version, rights) ");
        queryTpl.append("  VALUES %s ON CONFLICT(resource_unique_id) DO UPDATE SET name=EXCLUDED.name, version=EXCLUDED.version, creator_id=COALESCE(NULLIF(EXCLUDED.creator_id,''), NULLIF(r.creator_id, ''), ''), rights=COALESCE(EXCLUDED.rights, r.rights, '[]') RETURNING * ");
        queryTpl.append(")  ");
        queryTpl.append("SELECT upserted.id as resource_id,upserted.ent_id,upserted.resource_unique_id, ");
        queryTpl.append("       upserted.creator_id, upserted.version, upserted.application, upserted.resource_type, upserted.muted_by, upserted.trashed_by, upserted.rights, ");
        queryTpl.append("       fr.folder_id as folder_id, fr.user_id as user_id, f.trashed as folder_trash ");
        queryTpl.append("FROM upserted ");
        queryTpl.append("LEFT JOIN explorer.folder_resources fr ON upserted.id=fr.resource_id ");
        queryTpl.append("LEFT JOIN explorer.folders f ON fr.folder_id=f.id ");
        final String query = String.format(queryTpl.toString(), insertPlaceholder);
        return client.preparedQuery(query, tuple).map(rows->{
            final Map<Integer, ResouceSql> results = new HashMap<>();
            final List<ResouceSql> models = new ArrayList<>();
            for(final Row row : rows){
                models.add(mapRowToModel(row, model -> {
                    // set if not exists -> ensure uniqueness
                    results.putIfAbsent(model.id, model);
                    // get model to update
                    return results.get(model.id);
                }));
            }
            return models;
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
                final long version = row.getLong("version");
                final Object rights = row.getJson("rights");
                final ResouceSql res = new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version);
                if(rights != null) {
                    if (rights instanceof JsonArray) {
                        res.rights.addAll((JsonArray) rights);
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
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("SELECT resources.id as resource_id,resources.ent_id,resources.resource_unique_id, ");
        queryTpl.append("       resources.creator_id, resources.version, resources.application, resources.resource_type, resources.muted_by, resources.rights, ");
        queryTpl.append("       f.id as folder_id, fr.user_id as user_id, f.trashed as folder_trash ");
        queryTpl.append("FROM explorer.resources  ");
        queryTpl.append("LEFT JOIN explorer.folder_resources fr ON resources.id=fr.resource_id ");
        queryTpl.append("LEFT JOIN explorer.folders f ON fr.folder_id=f.id ");
        queryTpl.append("WHERE resources.ent_id IN (%s)");
        final String query = String.format(queryTpl.toString(), inPlaceholder);
        return client.preparedQuery(query, tuple).map(rows ->{
            final Set<ResouceSql> resources = new HashSet<>();
            for(final Row row : rows){
                resources.add(mapRowToModel(row, Function.identity()));
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

    /**
     * This method move resources into folders.
     * Links between resources and folders are materialized by a ResourceLink.
     * Each ResourceLink object contains a tuple (resourceId, folderId, updaterId) that allow us to create a relationship
     * between a resource and a folder for a specific user.
     * Reminder: we have a many-to-many relationship between resources and folders, but each user have one (or zero) folder related to each resource
     *
     * @param links a List of ResourceLink(folderId,resourceId,updaterId) to create or update
     * @return a List of ResouceSql that contains all infos about resource upserted with their related folders (and users)
     */
    public Future<List<ResouceSql>> moveTo(final List<ResourceLink> links){
        if(links.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final List<JsonObject> jsons = links.stream().map(e-> {
            return new JsonObject().put("resource_id", e.resourceId).put("folder_id", e.parentId).put("user_id", e.updaterId);
        }).collect(Collectors.toList());
        final Tuple tuple = PostgresClient.insertValues(jsons, Tuple.tuple(),"folder_id", "resource_id", "user_id");
        final String insertPlaceholder = PostgresClient.insertPlaceholders(jsons, 1,"folder_id", "resource_id", "user_id");
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("WITH updated AS ( ");
        queryTpl.append("   INSERT INTO explorer.folder_resources(folder_id, resource_id, user_id) VALUES %s ");
        queryTpl.append("   ON CONFLICT(resource_id, user_id) DO UPDATE SET folder_id=EXCLUDED.folder_id RETURNING * ");
        queryTpl.append(") ");
        queryTpl.append("SELECT upserted.id as resource_id,upserted.ent_id,upserted.resource_unique_id, ");
        queryTpl.append("       upserted.creator_id, upserted.version, upserted.application, upserted.resource_type, upserted.muted_by, upserted.rights, ");
        queryTpl.append("       f.id as folder_id, updated.user_id as user_id, f.trashed as folder_trash ");
        queryTpl.append("FROM explorer.resources AS upserted ");
        queryTpl.append("INNER JOIN updated ON updated.resource_id=upserted.id ");
        queryTpl.append("LEFT JOIN explorer.folders f ON updated.folder_id=f.id ");
        final String query = String.format(queryTpl.toString(), insertPlaceholder);
        return client.preparedQuery(query, tuple).map(rows->{
            final Map<Integer, ResouceSql> results = new HashMap<>();
            final List<ResouceSql> models = new ArrayList<>();
            for(final Row row : rows){
                models.add(mapRowToModel(row, model -> {
                    // set if not exists -> ensure uniqueness
                    results.putIfAbsent(model.id, model);
                    // get model to update
                    return results.get(model.id);
                }));
            }
            return models;
        });
    }

    /**
     * Map a ResourceSql java object from an sql Row
     * @param row an sql Row containing result of queries like: SELECT ... FROM resources join resource_folders join folder
     * @param getOrCreate a function that lets the caller using a new ResouceSql or returning an existing instance of ResourceSql.
     *                    Could be useful in case we have duplicate entries in a batch of queries and we want to merge the result
     * @return A ResouceSql object containing all info about a resource and related folders
     */
    private ResouceSql mapRowToModel(final Row row, final Function<ResouceSql, ResouceSql> getOrCreate){
        final Integer id = row.getInteger("resource_id");
        final String entId = row.getString("ent_id");
        final String userId = row.getString("user_id");
        final Integer folderId = row.getInteger("folder_id");
        final String creatorId = row.getString("creator_id");
        final String resourceUniqueId = row.getString("resource_unique_id");
        final String application = row.getString("application");
        final String resource_type = row.getString("resource_type");
        final Boolean folder_trash = row.getBoolean("folder_trash");
        final Object mutedBy = row.getJson("muted_by");
        final Object trashedBy = row.getJson("trashed_by");
        final long version = row.getLong("version");
        final Object rights = row.getJson("rights");
        final ResouceSql resource = getOrCreate.apply(new ResouceSql(entId, id, resourceUniqueId, creatorId, application, resource_type, version));
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
        if(mutedBy instanceof JsonObject) {
            resource.mutedBy.mergeIn((JsonObject) mutedBy);
        }
        if(trashedBy instanceof JsonObject) {
            resource.trashedBy.mergeIn((JsonObject) trashedBy);
        }
        if(rights != null){
            if(rights instanceof JsonArray){
                resource.rights.addAll((JsonArray) rights);
            }
        }
        return resource;
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

    /**
     * Trash resources for all users
     * @param idsToTrashForEverybody ids of resources to be trashed for all users
     * @param trashed whether a resource has to be trashed or restored
     * @return resources info after trash status update
     */
    public Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trashForAll(final Collection<Integer> idsToTrashForEverybody, final boolean trashed) {
        return client.transaction().compose(transaction->{
           final Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> future = this.trashForAll(transaction, idsToTrashForEverybody, trashed);
           return transaction.commit().compose(commit -> future);
        });
    }

    public Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trashForAll(final IPostgresTransaction transaction, final Collection<Integer> resourceIds, final boolean trashed){
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
                mapTrashed.put(id, new FolderExplorerDbSql.FolderTrashResult(id, parentOpt, application, resource_type, ent_id, Collections.emptyList()));
            }
        });
        return future.map(mapTrashed);
    }

    /**
     * Trash resources for a specific user
     * @param idsToTrashForUser ids of resources to be trashed for a specific user
     * @param userId id of user trashing the resources
     * @param trashed whether a resource has to be trashed or restored
     * @return basic resources info after trash status update
     */
    public Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trashForUser(final Collection<IdAndVersion> idsToTrashForUser, final String userId, final boolean trashed) {
        return client.transaction().compose(transaction->{
            final Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> future = this.trashForUser(transaction, idsToTrashForUser, userId, trashed);
            return transaction.commit().compose(commit -> future);
        });
    }

    public Future<Map<Integer, FolderExplorerDbSql.FolderTrashResult>> trashForUser(final IPostgresTransaction transaction, final Collection<IdAndVersion> resourceIds, final String userId, final boolean trashed){
        if (resourceIds.isEmpty()) {
            return Future.succeededFuture(new HashMap<>());
        }
        final Map<Integer, FolderExplorerDbSql.FolderTrashResult> mapTrashed = new HashMap<>();
        final Tuple tuple = PostgresClient.inTuple(Tuple.of(new JsonObject().put(userId, trashed)), resourceIds.stream().map(IdAndVersion::getId).collect(Collectors.toSet()));
        final String inPlaceholder = PostgresClient.inPlaceholder(resourceIds, 2);
        final String query = String.format("UPDATE explorer.resources SET trashed_by = trashed_by || $1 WHERE ent_id IN (%s) RETURNING *", inPlaceholder);
        final Future<RowSet<Row>> future = transaction.addPreparedQuery(query, tuple).onSuccess(rows->{
            for(final Row row : rows){
                final Integer id = row.getInteger("id");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final String ent_id = row.getString("ent_id");
                final Optional<Integer> parentOpt = Optional.empty();
                final List<String> trashedBy = ((JsonObject) row.getValue("trashed_by")).stream()
                        .filter(trashers -> trashers.getValue().toString().equals("true"))
                        .map(trashers -> trashers.getKey())
                        .collect(Collectors.toList());
                mapTrashed.put(id, new FolderExplorerDbSql.FolderTrashResult(id, parentOpt, application, resource_type, ent_id, trashedBy));
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

    public static class ResourceLink{
        final Number parentId;
        final Number resourceId;
        final String updaterId;
        public ResourceLink(final Number parentId, final Number resourceId, final String updaterId){
            this.parentId = parentId;
            this.resourceId = resourceId;
            this.updaterId = updaterId;
        }
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
        public final JsonObject mutedBy = new JsonObject();
        public final JsonObject trashedBy = new JsonObject();
        public final JsonArray rights = new JsonArray();
        public final String entId;
        public final Integer id;
        public final String resourceUniqId;
        public final String resourceType;
        public final String application;
        public final long version;

        public Integer getId() {
            return id;
        }

        public String getEntId() {
            return entId;
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
            "  INSERT INTO explorer.resources as r (ent_id, name,application,resource_type, resource_unique_id, creator_id, version, muted_by) " +
            "  VALUES %s ON CONFLICT(resource_unique_id) DO UPDATE SET name=EXCLUDED.name, version=EXCLUDED.version, muted_by=EXCLUDED.muted_by || r.muted_by RETURNING * " +
            ")  " +
            "SELECT upserted.id as resource_id,ent_id,resource_unique_id, creator_id, version, " +
            "       application, resource_type, " +
            "       fr.folder_id as folder_id, fr.user_id as user_id, muted_by " +
            "FROM upserted " +
            "LEFT JOIN explorer.folder_resources fr ON upserted.id=fr.resource_id ";

    private static final String UPDATE_MUTED_BY = "WITH updated AS (UPDATE explorer.resources " +
            "SET muted_by = muted_by || $1 " +
            "WHERE ent_id = $2 RETURNING *) " +
            "SELECT updated.id as resource_id,ent_id,resource_unique_id, creator_id, version, " +
            "       application, resource_type, " +
            "       fr.folder_id as folder_id, fr.user_id as user_id, muted_by " +
            "FROM updated " +
            "LEFT JOIN explorer.folder_resources fr ON updated.id=fr.resource_id ";
}
