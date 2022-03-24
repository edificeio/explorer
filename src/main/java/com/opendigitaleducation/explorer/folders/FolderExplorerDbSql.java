package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.postgres.PostgresClientPool;

import java.util.*;
import java.util.stream.Collectors;

public class FolderExplorerDbSql {
    private final PostgresClientPool pgPool;
    public FolderExplorerDbSql(final PostgresClient pool) {
        this.pgPool = pool.getClientPool();
    }
    protected String getTableName() { return "explorer.folders"; }

    protected List<String> getUpdateColumns() { return Arrays.asList("name", "parent_id"); }

    protected List<String> getColumns() { return Arrays.asList("name", "application", "resource_type", "parent_id", "creator_id", "creator_name"); }

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

    public static class FolderMoveResult {
        final Integer id;
        final Optional<Integer> parentId;
        final Optional<String> application;

        public FolderMoveResult(Integer id, Optional<Integer> parentId, String application) {
            this(id, parentId, Optional.ofNullable(application));
        }

        public FolderMoveResult(Integer id, Optional<Integer> parentId, Optional<String> application) {
            this.id = id;
            this.parentId = parentId;
            this.application = application;
        }
    }
}
