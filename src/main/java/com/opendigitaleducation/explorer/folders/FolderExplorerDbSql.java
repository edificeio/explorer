package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.postgres.PostgresClientPool;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class FolderExplorerDbSql {
    static Logger log = LoggerFactory.getLogger(FolderExplorerDbSql.class);
    private final PostgresClientPool pgPool;
    public FolderExplorerDbSql(final PostgresClient pool) {
        this.pgPool = pool.getClientPool();
    }

    public ResourceExplorerDbSql getResourceHelper() { return new ResourceExplorerDbSql(pgPool); }

    protected String getTableName() { return "explorer.folders"; }

    protected List<String> getUpdateColumns() { return Arrays.asList("name", "parent_id"); }

    protected List<String> getColumns() { return Arrays.asList("name", "application", "resource_type", "parent_id", "creator_id", "creator_name"); }

    protected List<String> getColumnsExt() { return Arrays.asList("name", "application", "resource_type", "parent_id", "creator_id", "creator_name", "ent_id", "parent_ent_id"); }

    protected String[] getColumnsExtArray() { return getColumnsExt().toArray(new String[getColumnsExt().size()]); }

    public Future<Map<String, ExplorerMessageForIngest>> updateParentEnt(){
        final String updateSubQuery = "(SELECT id,ent_id FROM explorer.folders) as subquery";
        final String updateTpl = "UPDATE explorer.folders SET parent_id = subquery.id, parent_ent_id=NULL FROM %s WHERE subquery.ent_id = parent_ent_id AND parent_ent_id IS NOT NULL AND parent_id IS NULL RETURNING * ";
        final String update = String.format(updateTpl, updateSubQuery);
        final Future<RowSet<Row>> updateFuture = pgPool.preparedQuery(update, Tuple.tuple());
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
                final ExplorerMessageForIngest message = new ExplorerMessageForIngest(ExplorerMessage.upsert(id.toString(), user, false));
                message.withParentId(Optional.ofNullable(parent_id).map(e -> Long.valueOf(e.toString())));
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
        queryTpl.append("WITH upserted AS ( ");
        queryTpl.append("  INSERT INTO explorer.folders as r (name,application,resource_type, parent_id, creator_id, creator_name, ent_id, parent_ent_id) ");
        queryTpl.append("  VALUES %s ON CONFLICT(ent_id) DO NOTHING RETURNING * ");
        queryTpl.append(")  ");
        queryTpl.append("SELECT * FROM upserted ");
        final String query = String.format(queryTpl.toString(), insertPlaceholder);
        return pgPool.preparedQuery(query, tuple).map(rows->{
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
            return pgPool.transaction().compose(transaction->{
                final Set<String> resourceEntIds = new HashSet<>();
                final List<Future> futures = new ArrayList<>();
                for(final ExplorerMessage res : resources){
                    if(res.getChildEntId().isPresent() && res.getChildEntId().get().size() > 0){
                        final String folderId = res.getId();
                        final String userId = res.getCreatorId();
                        final Set<String> resEntId = res.getChildEntId().get();
                        final String inQuery = PostgresClient.inPlaceholder(resEntId, 3);
                        final Tuple ituple = PostgresClient.inTuple(Tuple.of(userId, folderId), resEntId);
                        final StringBuilder iquery = new StringBuilder();
                        iquery.append("INSERT INTO explorer.folder_resources(folder_id, resource_id, user_id) ");
                        iquery.append("SELECT f.id, r.id, $1 as user_id FROM explorer.folders f, explorer.resources r ");
                        iquery.append(String.format("WHERE f.ent_id = $2 AND r.ent_id IN (%s)  ", inQuery));
                        futures.add(transaction.addPreparedQuery(iquery.toString(), ituple).onFailure(e->{
                           log.error("Failed to insert relationship for folderId="+folderId, e);
                        }));
                        resourceEntIds.addAll(resEntId);
                    }
                }
                return transaction.commit().compose(commit->{
                    return new ResourceExplorerDbSql(pgPool).getModelByEntIds(resourceEntIds).map(allResources->{
                        return new FolderUpsertResult(rows, allResources);
                    });
                });
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
        final String queryTpl = "UPDATE %s SET %s WHERE id = $1 RETURNING *";
        final String query = String.format(queryTpl, getTableName(), updatePlaceholder);
        return pgPool.preparedQuery(query, tuple).compose(rows->{
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
        return pgPool.preparedQuery(query.toString(), tuple).map(e->{
            final Row row = e.iterator().next();
            final Integer parentId = row.getInteger("parent_id");
            final String application = row.getString("application");
            return new FolderMoveResult(numId, Optional.ofNullable(parentId), application);
        });
    }

    public Future<Map<Integer, FolderMoveResult>> move(final Collection<Integer> ids, final Optional<String> newParent){
        return pgPool.transaction().compose(transaction->{
            final List<Future> futures = new ArrayList<>();
            for(final Integer numId: ids){
                final StringBuilder query = new StringBuilder();
                final Integer numParentId = newParent.map(e->{
                    if(ExplorerConfig.ROOT_FOLDER_ID.equalsIgnoreCase(e)){
                        return null;
                    }
                    return Integer.valueOf(e);
                }).orElse(null);
                final Tuple tuple = Tuple.of(numParentId,numId);
                query.append("UPDATE explorer.folders SET parent_id=$1 WHERE id=$2");
                futures.add(transaction.addPreparedQuery(query.toString(), tuple));
            }
            final Tuple tuple = PostgresClient.inTuple(Tuple.tuple(), ids);
            final String placeholder = PostgresClient.inPlaceholder(ids, 1);
            final String query = String.format("SELECT id, parent_id, application FROM explorer.folders WHERE id IN (%s) ", placeholder);
            final Future<RowSet<Row>> promiseRows = transaction.addPreparedQuery(query.toString(), tuple);
            futures.add(promiseRows);
            return transaction.commit().compose(e->{
               return  CompositeFuture.all(futures).map(results->{
                   final RowSet<Row> rows = promiseRows.result();
                   final Map<Integer, FolderMoveResult> mappingParentByChild = new HashMap<>();
                   for(final Row row : rows){
                       final Integer id = row.getInteger("id");
                       final Integer parentId = row.getInteger("parent_id");
                       final String application = row.getString("application");
                       final Optional<Integer> parentOpt = Optional.ofNullable(parentId);
                       mappingParentByChild.put(id, new FolderMoveResult(id, parentOpt, application));
                   }
                   return mappingParentByChild;
               });
            });
        });
    }

    public Future<List<ResourceExplorerDbSql.ResourceId>> getResourcesIdsForFolders(final Set<Integer> folderIds){
        final ResourceExplorerDbSql resSql = new ResourceExplorerDbSql(pgPool);
        return resSql.getIdsByFolderIds(folderIds);
    }

    public Future<FolderTrashResults> trash(final Collection<Integer> folderIds, final Collection<Integer> resourceIds, final boolean trashed){
        if(!resourceIds.isEmpty() && !folderIds.isEmpty()){
            return Future.succeededFuture(new FolderTrashResults());
        }
        return pgPool.transaction().compose(transaction->{
            final List<Future> futures = new ArrayList<>();
            final FolderTrashResults mapTrashed = new FolderTrashResults();
            if(!resourceIds.isEmpty()){
                final ResourceExplorerDbSql resSql = new ResourceExplorerDbSql(pgPool);
                futures.add(resSql.trash(transaction, resourceIds, trashed).onSuccess(resources->{
                    mapTrashed.resources.putAll(resources);
                }));
            }
            if(!folderIds.isEmpty()){
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(trashed), folderIds);
                final String inPlaceholder = PostgresClient.inPlaceholder(folderIds, 2);
                final String query = String.format("UPDATE explorer.folders SET trashed=$1 WHERE id IN (%s) RETURNING *", inPlaceholder);
                futures.add(transaction.addPreparedQuery(query, tuple).onSuccess(rows->{
                    for(final Row row : rows){
                        final Integer id = row.getInteger("id");
                        final Integer parentId = row.getInteger("parent_id");
                        final String application = row.getString("application");
                        final String ent_id = row.getString("ent_id");
                        final Optional<Integer> parentOpt = Optional.ofNullable(parentId);
                        mapTrashed.folders.put(id, new FolderTrashResult(id, parentOpt, application, ExplorerConfig.FOLDER_TYPE, ent_id));
                    }
                }));
            }
            return transaction.commit().compose(commit->{
                return CompositeFuture.all(futures);
            }).map(mapTrashed);
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
        query.append("   UNION ALL ");
        query.append("   SELECT f2.id, f2.name, f2.parent_id, f2.application FROM explorer.folders f2, ancestors  WHERE f2.id = ancestors.parent_id ");
        query.append(") ");
        query.append("SELECT * FROM ancestors;");
        return pgPool.preparedQuery(query.toString(), inTuple).map(ancestors -> {
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
        query.append("   UNION ALL ");
        query.append("   SELECT f2.id, f2.name, f2.parent_id, f2.application FROM explorer.folders f2, ancestors  WHERE f2.parent_id = ancestors.id ");
        query.append(") ");
        query.append("SELECT * FROM ancestors;");
        return pgPool.preparedQuery(query.toString(), inTuple).map(ancestors -> {
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
        final String query = String.format("SELECT f1.id, f1.parent_id FROM explorer.folders f1 WHERE f1.parent_id IN (%s) OR f1.id IN (%s) ", inPlaceholder,inPlaceholder);
        return pgPool.preparedQuery(query, inTuple).map(ancestors -> {
            final Map<String, FolderRelationship> relationShips = new HashMap<>();
            for (final Row row : ancestors) {
                //get parent of each
                final String id = row.getInteger("id").toString();
                final Integer parent_id = row.getInteger("parent_id");
                relationShips.putIfAbsent(id, new FolderRelationship(id));
                if(parent_id != null){
                    final String parentIdStr = parent_id.toString();
                    relationShips.putIfAbsent(parentIdStr, new FolderRelationship(parentIdStr));
                    //add to children
                    relationShips.get(parentIdStr).childrenIds.add(id);
                    //set parent
                    relationShips.get(id).parentId = Optional.of(parentIdStr);
                }
            }
            return relationShips;
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
        public Optional<String> parentId = Optional.empty();
        public final List<String> childrenIds = new ArrayList<>();

        public FolderRelationship(String id) {
            this.id = id;
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
        public final Optional<String> application;

        public FolderMoveResult(Integer id, Optional<Integer> parentId, String application) {
            this(id, parentId, Optional.ofNullable(application));
        }

        public FolderMoveResult(Integer id, Optional<Integer> parentId, Optional<String> application) {
            this.id = id;
            this.parentId = parentId;
            this.application = application;
        }
    }

    public static class FolderTrashResults {
        public final Map<Integer, FolderTrashResult> folders = new HashMap<>();
        public final Map<Integer, FolderTrashResult> resources = new HashMap<>();
    }

    public static class FolderTrashResult {
        public final Integer id;
        public final Optional<Integer> parentId;
        public final Optional<String> application;
        public final Optional<String> resourceType;
        public final Optional<String> entId;

        public FolderTrashResult(Integer id, Optional<Integer> parentId, String application, String resourceType, String entId) {
            this(id, parentId, Optional.ofNullable(application), Optional.ofNullable(resourceType), Optional.ofNullable(entId));
        }

        public FolderTrashResult(Integer id, Optional<Integer> parentId, Optional<String> application, Optional<String> resourceType, Optional<String> entId) {
            this.id = id;
            this.parentId = parentId;
            this.application = application;
            this.resourceType = resourceType;
            this.entId = entId;
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
