package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import static java.lang.System.currentTimeMillis;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.IdAndVersion;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FolderExplorerDbSql {
    static Logger log = LoggerFactory.getLogger(FolderExplorerDbSql.class);
    private final IPostgresClient client;

    public static final String TABLE = "explorer.folders";
    public FolderExplorerDbSql(final IPostgresClient client) {
        this.client = client;
    }

    public ResourceExplorerDbSql getResourceHelper() { return new ResourceExplorerDbSql(client); }

    protected String getTableName() { return TABLE; }

    protected List<String> getUpdateColumns() { return Arrays.asList("name", "parent_id"); }

    protected List<String> getColumns() { return Arrays.asList("name", "application", "resource_type", "parent_id", "creator_id", "creator_name"); }

    protected List<String> getColumnsExt() { return Arrays.asList("name", "application", "resource_type", "parent_id", "creator_id", "creator_name", "ent_id", "parent_ent_id"); }

    protected String[] getColumnsExtArray() { return getColumnsExt().toArray(new String[getColumnsExt().size()]); }

    public Future<List<String>> deleteTrashedFolderIds(){
        final String query = "DELETE FROM explorer.folders WHERE trashed IS TRUE RETURNING id";
        final Future<RowSet<Row>> updateFuture = client.preparedQuery(query, Tuple.tuple());
        return updateFuture.onFailure(e -> {
            log.error("Select folders failed:", e);
        }).compose(res -> {
            final List<String> ids = new ArrayList<>();
            for (final Row row : res) {
                final Object id = row.getValue("id");
                ids.add(id.toString());
            }
            return Future.succeededFuture(ids);
        });
    }
    public Future<List<String>> selectFolderIds(){
        final String query = "SELECT id FROM explorer.folders";
        final Future<RowSet<Row>> updateFuture = client.preparedQuery(query, Tuple.tuple());
        return updateFuture.onFailure(e -> {
            log.error("Select folders failed:", e);
        }).compose(res -> {
            final List<String> ids = new ArrayList<>();
            for (final Row row : res) {
                final Object id = row.getValue("id");
                ids.add(id.toString());
            }
            return Future.succeededFuture(ids);
        });
    }
    public Future<List<String>> selectTrashedFolderIds(){
        final String query = "SELECT id FROM explorer.folders WHERE trashed IS TRUE";
        final Future<RowSet<Row>> updateFuture = client.preparedQuery(query, Tuple.tuple());
        return updateFuture.onFailure(e -> {
            log.error("Select folders failed:", e);
        }).compose(res -> {
            final List<String> ids = new ArrayList<>();
            for (final Row row : res) {
                final Object id = row.getValue("id");
                ids.add(id.toString());
            }
            return Future.succeededFuture(ids);
        });
    }

    public Future<Map<String, ExplorerMessageForIngest>> updateParentEnt(){
        final long now = currentTimeMillis();
        final String updateSubQuery = "(SELECT id,ent_id FROM explorer.folders) as subquery";
        final String updateTpl = "UPDATE explorer.folders SET parent_id = subquery.id, parent_ent_id=NULL FROM %s WHERE subquery.ent_id = parent_ent_id AND parent_ent_id IS NOT NULL AND parent_id IS NULL AND subquery.id <> folders.id RETURNING * ";
        final String update = String.format(updateTpl, updateSubQuery);
        final Future<RowSet<Row>> updateFuture = client.preparedQuery(update, Tuple.tuple());
        return updateFuture.onFailure(e -> {
                log.error("Update folders parent failed:", e);
        }).compose(res -> {
            final Map<String, ExplorerMessageForIngest> upserted = new HashMap<>();
            for (final Row row : res) {
                final Object id = row.getValue("id");
                final Object parent_id = row.getValue("parent_id");
                final String creator_id = row.getString("creator_id");
                final String creator_name = row.getString("creator_name");
                final String ent_id = row.getString("ent_id");
                final UserInfos user = new UserInfos();
                user.setUserId(creator_id);
                user.setUsername(creator_name);
                // TODO JBER check version to set
                final ExplorerMessageForIngest message = new ExplorerMessageForIngest(ExplorerMessage.upsert(
                        new IdAndVersion(id.toString(), currentTimeMillis()), user, false,
                        row.getString("application"), row.getString("resource_type"), row.getString("resource_type")
                        ));
                message.withParentId(Optional.ofNullable(parent_id).map(e -> Long.valueOf(e.toString())));
                message.withVersion(System.currentTimeMillis()).withSkipCheckVersion(true);
                upserted.put(ent_id, message);
            }
            return Future.succeededFuture(upserted);
        });
    }

    public Future<FolderUpsertResult> upsert(final Collection<? extends ExplorerMessage> resources){
        if(resources.isEmpty()){
            return Future.succeededFuture(new FolderUpsertResult());
        }
        //must do update to return
        final List<JsonObject> resourcesList = resources.stream().map(e->{
            final JsonObject params = new JsonObject().put("ent_id", e.getId())
                    .put("application",e.getApplication())
                    .put("resource_type", e.getResourceType())
                    .put("creator_id", e.getCreatorId())
                    .put("creator_name", e.getCreatorName())
                    .put("name", e.getName());
            if(e.getParentEntId().isPresent()){
                params.put("parent_ent_id", e.getParentEntId().get());
            }
            return params;
        }).collect(Collectors.toList());
        //(only one upsert per ent_id)
        final Map<String, JsonObject> resourcesMap = new HashMap<>();
        for(final JsonObject json : resourcesList){
            resourcesMap.put(json.getString("ent_id"), json);
        }
        final Map<String, Object> defaultVal = new HashMap<>();
        final Collection<JsonObject> resourcesColl = resourcesMap.values();
        final Tuple tuple = PostgresClient.insertValues(resourcesColl, Tuple.tuple(), defaultVal, getColumnsExtArray());
        final String insertPlaceholder = PostgresClient.insertPlaceholders(resourcesColl, 1, getColumnsExtArray());
        final StringBuilder queryTpl = new StringBuilder();
        //do update on conflict (because of returning)
        queryTpl.append("WITH upserted AS ( ");
        queryTpl.append("  INSERT INTO explorer.folders as r (name,application,resource_type, parent_id, creator_id, creator_name, ent_id, parent_ent_id) ");
        // keep explorer name if non empty else use migration name else empty name
        queryTpl.append("  VALUES %s ON CONFLICT(ent_id) DO UPDATE SET name = COALESCE(NULLIF(r.name,''), EXCLUDED.name, '') RETURNING * ");
        queryTpl.append(")  ");
        queryTpl.append("SELECT * FROM upserted ");
        final String query = String.format(queryTpl.toString(), insertPlaceholder);
        return client.preparedQuery(query, tuple).map(rows->{
            final Map<String, JsonObject> results = new HashMap<>();
            for(final Row row : rows){
                final Object id = row.getValue("id");
                final String name = row.getString("name");
                final String application = row.getString("application");
                final String resource_type = row.getString("resource_type");
                final Object parent_id = row.getValue("parent_id");
                final String creator_id = row.getString("creator_id");
                final String creator_name = row.getString("creator_name");
                final String parent_ent_id = row.getString("parent_ent_id");
                final String ent_id = row.getString("ent_id");
                final JsonObject json = new JsonObject().put("name",name).put("id",id).put("parent_ent_id",parent_ent_id)
                        .put("application", application).put("resource_type", resource_type).put("parent_id", parent_id)
                        .put("creator_id", creator_id).put("creator_name", creator_name).put("ent_id", ent_id);
                results.put(ent_id,json);
            }
            return (results);
        }).onFailure(e->{
            log.error("Failed to upsert folders:", e);
        }).compose(rows -> {
            //fetch folder
            return client.transaction(sqlConnection -> {
                final Set<String> resourceEntIds = new HashSet<>();
                final List<Future<?>> futures = new ArrayList<>();
                for(final ExplorerMessage res : resources){
                    if(res.getChildEntId().isPresent() && res.getChildEntId().get().size() > 0){
                        final String folderId = res.getId();
                        final String userId = res.getCreatorId();
                        final Set<String> resEntId = res.getChildEntId().get();
                        final String inQuery = PostgresClient.inPlaceholder(resEntId, 3);
                        final Tuple ituple = PostgresClient.inTuple(Tuple.of(userId, folderId), resEntId);
                        /**
                         * This query create or update a relationship between 1 folder and 1 resource
                         * Folder and resources have a many-to-many relationships but
                         * - for a specific user only 1 folder can be related to 1 resource
                         * - 1 resource can be related to many folders (each owned by different users)
                         * - 1 folder can have many resources (each from the same user)
                         *
                         * For theses reasons, upsert use the tupe (user_id,resource_id) as key for the upsert
                         *
                         * To Avoid many queries we are using "WITH" operator:
                         * - 1 query for the upsert
                         * - 1 query to fetch resources and folders
                         *
                         * So after upserting we are returning resources infos with alls folders (and users) related to each resources
                         *
                         */
                        final StringBuilder upsertQuery = new StringBuilder();
                        upsertQuery.append("WITH updated AS ( ");
                        upsertQuery.append("     INSERT INTO explorer.folder_resources(folder_id, resource_id, user_id) ");
                        upsertQuery.append("     SELECT f.id, r.id, $1 as user_id FROM explorer.folders f, explorer.resources r ");
                        upsertQuery.append(String.format("WHERE f.ent_id = $2 AND r.ent_id IN (%s)  ", inQuery));
                        upsertQuery.append("     ON CONFLICT(user_id,resource_id) DO NOTHING RETURNING * ");
                        upsertQuery.append(") ");
                        upsertQuery.append("SELECT upserted.id as resource_id,upserted.ent_id,upserted.resource_unique_id, ");
                        upsertQuery.append("       upserted.creator_id, upserted.version, upserted.application, upserted.resource_type, upserted.muted_by, upserted.rights, ");
                        upsertQuery.append("       f.id as folder_id, updated.user_id as user_id, f.trashed as folder_trash ");
                        upsertQuery.append("FROM explorer.resources AS upserted ");
                        upsertQuery.append("INNER JOIN updated ON updated.resource_id=upserted.id ");
                        upsertQuery.append("LEFT JOIN explorer.folders f ON updated.folder_id=f.id ");
                        futures.add(sqlConnection.preparedQuery(upsertQuery.toString()).execute(ituple).onFailure(e->{
                           log.error("Failed to insert relationship for folderId="+folderId, e);
                        }));
                        resourceEntIds.addAll(resEntId);
                    }
                }
                return Future.all(futures).compose(result-> new ResourceExplorerDbSql(client).getModelByEntIds(resourceEntIds).map(allResources-> new FolderUpsertResult(rows, allResources)));
            });
        });
    }

    public final Future<ResourceExplorerDbSql.FolderSql> update(final String id, final JsonObject source){
        beforeCreateOrUpdate(source);
        final Tuple tuple = Tuple.tuple();
        tuple.addValue(Integer.valueOf(id));
        final List<String> columnToUpdate = new ArrayList<>(getUpdateColumns());
        if(!source.containsKey("name")){
            columnToUpdate.remove("name");
        }
        final String updatePlaceholder = PostgresClient.updatePlaceholders(source, 2, columnToUpdate,tuple);
        final String queryTpl = "UPDATE %s SET %s, updated_at=NOW() WHERE id = $1 RETURNING *";
        final String query = String.format(queryTpl, getTableName(), updatePlaceholder);
        return client.preparedQuery(query, tuple).compose(rows->{
           for(final Row row : rows){
               final Integer idDb = row.getInteger("id");
               final String creator_id = row.getString("creator_id");
               final ResourceExplorerDbSql.FolderSql sql = new ResourceExplorerDbSql.FolderSql(idDb,creator_id);
               return Future.succeededFuture(sql);
           }
           return Future.failedFuture("folder.notfound");
        });
    }

    public Future<FolderMoveResult> move(final String id, final Optional<String> newParent){
        final StringBuilder query = new StringBuilder();
        final Integer numId = Integer.valueOf(id);
        final Integer numParentId = newParent.map(e->Integer.valueOf(e)).orElse(null);
        final Tuple tuple = Tuple.of(numId, numParentId,numId);
        query.append("WITH old AS (SELECT parent_id, application FROM explorer.folders WHERE id = $1) ");
        query.append("UPDATE explorer.folders SET parent_id=$2 WHERE id=$3 RETURNING (SELECT parent_id, application FROM old);");
        return client.preparedQuery(query.toString(), tuple).map(e->{
            final Row row = e.iterator().next();
            final Integer parentId = row.getInteger("parent_id");
            final String application = row.getString("application");
            return new FolderMoveResult(numId, Optional.ofNullable(parentId), application);
        });
    }

    public Future<Map<Integer, FolderMoveResult>> move(final Collection<Integer> ids, final Optional<String> newParent){
        return client.transaction(sqlConnection -> {
            final List<Future<?>> futures = new ArrayList<>();
	        final Tuple tuple = PostgresClient.inTuple(Tuple.tuple(), ids);
	        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
            // get all old parents and store in map
            final Map<Integer, Integer> oldParentById = new HashMap<>();
            final String queryOldParent = String.format("SELECT id, parent_id FROM explorer.folders WHERE id IN (%s) ", inPlaceholder);
			final Future<RowSet<Row>> promiseOldParent = sqlConnection.preparedQuery(queryOldParent).execute(tuple).onSuccess(rows->{
				for(final Row row : rows){
					oldParentById.put(row.getInteger("id"), row.getInteger("parent_id"));
				}
			});
            futures.add(promiseOldParent);
            // iterate on each id to update parent
            for(final Integer numId: ids){
                final StringBuilder query = new StringBuilder();
                final Integer numParentId = newParent.map(e->{
                    if(ExplorerConfig.ROOT_FOLDER_ID.equalsIgnoreCase(e)){
                        return null;
                    }
                    return Integer.valueOf(e);
                }).orElse(null);
                final Tuple tupleUpdate = Tuple.of(numParentId,numId);
                query.append("UPDATE explorer.folders SET parent_id=$1 WHERE id=$2");
                futures.add(sqlConnection.preparedQuery(query.toString()).execute(tupleUpdate));
            }
            // get all new parents
            final String query = String.format("SELECT id, parent_id, application FROM explorer.folders WHERE id IN (%s) ", inPlaceholder);
            final Future<RowSet<Row>> promiseRows = sqlConnection.preparedQuery(query).execute(tuple);
            futures.add(promiseRows);
            return Future.all(futures).map(results->{
                final RowSet<Row> rows = promiseRows.result();
                final Map<Integer, FolderMoveResult> mappingParentByChild = new HashMap<>();
                for(final Row row : rows){
                    final Integer id = row.getInteger("id");
                    final Integer parentId = row.getInteger("parent_id");
                    final String application = row.getString("application");
                    final Optional<Integer> parentOpt = Optional.ofNullable(parentId);
                    final Optional<Integer> oldParentOpt = Optional.ofNullable(oldParentById.get(id));
					mappingParentByChild.put(id, new FolderMoveResult(id, parentOpt, oldParentOpt,application));
                }
                return mappingParentByChild;
            });
        });
    }

    public Future<List<ResourceExplorerDbSql.ResourceId>> getResourcesIdsForFolders(final Set<Integer> folderIds){
        final ResourceExplorerDbSql resSql = new ResourceExplorerDbSql(client);
        return resSql.getIdsByFolderIds(folderIds);
    }

    public Future<FolderTrashResults> trash(final Collection<Integer> folderIds, final boolean trashed){
        if(folderIds.isEmpty()){
            return Future.succeededFuture(new FolderTrashResults());
        }
        return client.transaction(sqlConnection -> {
            final List<Future<?>> futures = new ArrayList<>();
            final FolderTrashResults mapTrashed = new FolderTrashResults();
            if(!folderIds.isEmpty()){
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(trashed), folderIds);
                final String inPlaceholder = PostgresClient.inPlaceholder(folderIds, 2);
                final String query = String.format("UPDATE explorer.folders SET trashed=$1 WHERE id IN (%s) RETURNING *", inPlaceholder);
                futures.add(sqlConnection.preparedQuery(query).execute(tuple).onSuccess(rows->{
                    for(final Row row : rows){
                        final Integer id = row.getInteger("id");
                        final Integer parentId = row.getInteger("parent_id");
                        final String application = row.getString("application");
                        final String ent_id = row.getString("ent_id");
                        final Optional<Integer> parentOpt = Optional.ofNullable(parentId);
                        mapTrashed.folders.put(id, new FolderTrashResult(id, parentOpt, application, ExplorerConfig.FOLDER_TYPE, ent_id, Collections.emptyList()));
                    }
                }));
            }
            return Future.all(futures).map(mapTrashed);
        });
    }

    protected void beforeCreateOrUpdate(final JsonObject source){
        final Object parentId = source.getValue("parentId");
        if(parentId instanceof String){
            if(ExplorerConfig.ROOT_FOLDER_ID.equalsIgnoreCase(parentId.toString())){
                source.remove("parentId");
            }else{
                source.put("parentId", Integer.valueOf(parentId.toString()));
            }
        }
    }

    public Future<Map<String, FolderAncestor>> getAncestors(final Set<Integer> ids) {
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final Tuple inTuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        final StringBuilder query = new StringBuilder();
        query.append("WITH RECURSIVE ancestors(id,name, parent_id, application) AS ( ");
        query.append(String.format("   SELECT f1.id, f1.name, f1.parent_id, f1.application FROM explorer.folders f1 WHERE f1.id IN (%s) ", inPlaceholder));
        // remove duplicate and avoid infinite loop
        query.append("   UNION ");
        query.append("   SELECT f2.id, f2.name, f2.parent_id, f2.application FROM explorer.folders f2, ancestors  WHERE f2.id = ancestors.parent_id ");
        query.append(") ");
        query.append("SELECT * FROM ancestors;");
        return client.preparedQuery(query.toString(), inTuple).map(ancestors -> {
            final Map<Integer, Integer> parentById = new HashMap<>();
            final Map<Integer, String> applicationById = new HashMap<>();
            for (final Row row : ancestors) {
                //get parent of each
                final Integer id = row.getInteger("id");
                final Integer parent_id = row.getInteger("parent_id");
                parentById.put(id, parent_id);
                applicationById.put(id, row.getString("application"));
            }
            //then get ancestors of each by recursion
            final Map<String, FolderAncestor> map = new HashMap<>();
            for (final Integer id : ids) {
                if(id != null){
                    final String application = applicationById.get(id);
                    final List<Integer> ancestorIds = getAncestors(parentById, id);
                    final List<Integer> ancestorsSafe = ancestorIds.stream().filter(e -> e!= null).collect(Collectors.toList());
                    final List<String> ancestorIdsStr = ancestorsSafe.stream().map(e -> e.toString()).collect(Collectors.toList());
                    map.put(id.toString(), new FolderAncestor(id.toString(), application, ancestorIdsStr));
                }
            }
            return map;
        });
    }

    public Future<Map<String, FolderDescendant>> getDescendants(final Set<Integer> ids) {
        if(ids.isEmpty()){
            return Future.succeededFuture(new HashMap<>());
        }
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final Tuple inTuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        final StringBuilder query = new StringBuilder();
        query.append("WITH RECURSIVE ancestors(id,name, parent_id, application) AS ( ");
        query.append(String.format("   SELECT f1.id, f1.name, f1.parent_id, f1.application FROM explorer.folders f1 WHERE f1.id IN (%s) ", inPlaceholder));
        // remove duplicate and avoid infinite loop
        query.append("   UNION ");
        query.append("   SELECT f2.id, f2.name, f2.parent_id, f2.application FROM explorer.folders f2, ancestors  WHERE f2.parent_id = ancestors.id ");
        query.append(") ");
        query.append("SELECT * FROM ancestors;");
        return client.preparedQuery(query.toString(), inTuple).map(ancestors -> {
            final Map<Integer, Set<Integer>> childrenById = new HashMap<>();
            final Map<Integer, String> applicationById = new HashMap<>();
            for (final Row row : ancestors) {
                //get parent of each
                final Integer id = row.getInteger("id");
                final Integer parent_id = row.getInteger("parent_id");
                final Set<Integer> children = childrenById.getOrDefault(parent_id, new HashSet<>());
                children.add(id);
                if(parent_id != null){
                    childrenById.put(parent_id, children);
                }
                applicationById.put(id, row.getString("application"));
            }
            //then get ancestors of each by recursion
            final Map<String, FolderDescendant> map = new HashMap<>();
            for (final Integer id : ids) {
                if(id != null){
                    final String application = applicationById.get(id);
                    final Set<Integer> descendantIds = getDescendants(childrenById, id);
                    final List<Integer> descendantSafe = descendantIds.stream().filter(e -> e!= null).collect(Collectors.toList());
                    final List<String> descendantIdsStr = descendantSafe.stream().map(e -> e.toString()).collect(Collectors.toList());
                    map.put(id.toString(), new FolderDescendant(id.toString(), application, descendantIdsStr));
                }
            }
            return map;
        });
    }

    public Future<Map<String, FolderRelationship>> getRelationships(final Set<Integer> ids) {
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final Tuple inTuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        final String query = String.format("SELECT f1.id, f1.parent_id, f1.application FROM explorer.folders f1 WHERE f1.parent_id IN (%s) OR f1.id IN (%s) ", inPlaceholder,inPlaceholder);
        return client.preparedQuery(query, inTuple).map(ancestors -> {
            final Map<String, FolderRelationship> relationShips = new HashMap<>();
            for (final Row row : ancestors) {
                //get parent of each
                final String id = row.getInteger("id").toString();
                final String application = row.getString("application");
                final Integer parent_id = row.getInteger("parent_id");
                relationShips.putIfAbsent(id, new FolderRelationship(id, application));
                if(parent_id != null){
                    final String parentIdStr = parent_id.toString();
                    relationShips.putIfAbsent(parentIdStr, new FolderRelationship(parentIdStr, application));
                    //add to children
                    relationShips.get(parentIdStr).childrenIds.add(id);
                    //set parent
                    relationShips.get(id).parentId = Optional.of(parentIdStr);
                }
            }
            return relationShips;
        });
    }

    public Future<Integer> countByIds(final Collection<Integer> ids) {
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final Tuple inTuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        final String query = String.format("SELECT COUNT(f1.id) as count FROM explorer.folders f1 WHERE f1.id IN (%s) ", inPlaceholder,inPlaceholder);
        return client.preparedQuery(query, inTuple).map(ancestors -> {
            for (final Row row : ancestors) {
                //get parent of each
                return row.getInteger("count");
            }
            return 0;
        });
    }

    public List<Integer> getAncestors(final Map<Integer, Integer> parentById, final Integer root) {
        final Integer parent = parentById.get(root);
        final List<Integer> all = new ArrayList<>();
        if (parent != null) {
            final List<Integer> ancestors = getAncestors(parentById, parent);
            all.addAll(ancestors);
        }
        all.add(parent);
        return all;
    }

    public Set<Integer> getDescendants(final Map<Integer, Set<Integer>> childrenById, final Integer root) {
        final Set<Integer> children = childrenById.getOrDefault(root, new HashSet<>());
        final Set<Integer> all = new HashSet<>();
        for(final Integer child : children){
            final Set<Integer> descendant = getDescendants(childrenById, child);
            all.addAll(descendant);
        }
        all.addAll(children);
        return all;
    }

    public static class FolderRelationship{
        public final String id;
        public final String application;
        public Optional<String> parentId = Optional.empty();
        public final List<String> childrenIds = new ArrayList<>();

        public FolderRelationship(String id, String application) {
            this.id = id;
            this.application = application;
        }
    }

    public static class FolderAncestor{
        public final String id;
        public final Optional<String> application;
        public final List<String> ancestorIds;

        public FolderAncestor(final String id, final List<String> ancestorIds) {
            this(id, null, ancestorIds);
        }

        public FolderAncestor(final String id, final String app, final List<String> ancestorIds) {
            this.application = Optional.ofNullable(app);
            this.ancestorIds = ancestorIds;
            this.id = id;
        }
    }

    public static class FolderDescendant{
        public final String id;
        public final Optional<String> application;
        public final List<String> descendantIds;

        public FolderDescendant(final String id, final List<String> descendantIds) {
            this(id, null, descendantIds);
        }

        public FolderDescendant(final String id, final String app, final List<String> descendantIds) {
            this.application = Optional.ofNullable(app);
            this.descendantIds = descendantIds;
            this.id = id;
        }
    }

    public static class FolderMoveResult {
        public final Integer id;
        public final Optional<Integer> parentId;
        public final Optional<Integer> oldParentId;
        public final Optional<String> application;

        public FolderMoveResult(Integer id, Optional<Integer> parentId, String application) {
            this(id, parentId, Optional.empty(), Optional.ofNullable(application));
        }

        public FolderMoveResult(Integer id, Optional<Integer> parentId, Optional<Integer> oldParentId, String application) {
            this(id, parentId, oldParentId, Optional.ofNullable(application));
        }

        public FolderMoveResult(Integer id, Optional<Integer> parentId, Optional<Integer> oldParentId, Optional<String> application) {
            this.id = id;
            this.parentId = parentId;
            this.oldParentId = oldParentId;
            this.application = application;
        }
    }

    public static class FolderTrashResults {
        public final Map<Integer, FolderTrashResult> folders = new HashMap<>();
    }

    public static class FolderTrashResult {
        public final Integer id;
        public final Optional<Integer> parentId;
        public final Optional<String> application;
        public final Optional<String> resourceType;
        public final Optional<String> entId;
        public final List<String> trashedBy;

        public FolderTrashResult(Integer id, Optional<Integer> parentId, String application, String resourceType, String entId, List<String> trashedBy) {
            this(id, parentId, Optional.ofNullable(application), Optional.ofNullable(resourceType), Optional.ofNullable(entId), trashedBy);
        }

        public FolderTrashResult(Integer id, Optional<Integer> parentId, Optional<String> application, Optional<String> resourceType, Optional<String> entId, List<String> trashedBy) {
            this.id = id;
            this.parentId = parentId;
            this.application = application;
            this.resourceType = resourceType;
            this.entId = entId;
            this.trashedBy = trashedBy;
        }
    }



    public static class FolderUpsertResult {
        public final Map<String, JsonObject> folderEntById;
        public final Set<ResourceExplorerDbSql.ResouceSql> resourcesUpdated;

        public FolderUpsertResult() {
            this(new HashMap<>(), new HashSet<>());
        }

        public FolderUpsertResult(final Map<String, JsonObject> folderEntById, Set<ResourceExplorerDbSql.ResouceSql> resourcesUpdated) {
            this.folderEntById = folderEntById;
            this.resourcesUpdated = resourcesUpdated;
        }
    }
}
