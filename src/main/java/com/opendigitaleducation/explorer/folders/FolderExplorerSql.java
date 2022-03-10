package com.opendigitaleducation.explorer.folders;

import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.impl.ExplorerDbSql;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class FolderExplorerSql extends ExplorerDbSql {

    public FolderExplorerSql(final PostgresClient pool) {
        super(pool.getClientPool());
    }

    @Override
    protected String getTableName() { return "explorer.folders"; }

    @Override
    protected List<String> getColumns() { return Arrays.asList("name", "application", "resource_type", "parent_id", "creator_id", "creator_name"); }

    @Override
    protected List<String> getMessageFields() { return Arrays.asList("name", "application", "resourceType", "parentId", "creator_id", "creator_name"); }

    @Override
    public Future<List<String>> createAll(final UserInfos user, final List<JsonObject> sources) {
        for(final JsonObject source : sources){
            beforeCreateOrUpdate(source);
        }
        return super.createAll(user, sources);
    }

    public final Future<ResourceExplorerCrudSql.FolderSql> update(final String id, final JsonObject source){
        beforeCreateOrUpdate(source);
        final Tuple tuple = Tuple.tuple();
        tuple.addValue(Integer.valueOf(id));
        final String updatePlaceholder = PostgresClient.updatePlaceholders(source, 2, getColumns(),tuple);
        final String queryTpl = "UPDATE %s SET %s WHERE id = $1 RETURNING *";
        final String query = String.format(queryTpl, getTableName(), updatePlaceholder);
        return pgPool.preparedQuery(query, tuple).compose(rows->{
           for(final Row row : rows){
               final Integer idDb = row.getInteger("id");
               final String creator_id = row.getString("creator_id");
               final ResourceExplorerCrudSql.FolderSql sql = new ResourceExplorerCrudSql.FolderSql(idDb,creator_id);
               return Future.succeededFuture(sql);
           }
           return Future.failedFuture("folder.notfound");
        });
    }

    public Future<Optional<Integer>> move(final String id, final Optional<String> newParent){
        final StringBuilder query = new StringBuilder();
        final Integer numId = Integer.valueOf(id);
        final Integer numParentId = newParent.map(e->Integer.valueOf(e)).orElse(null);
        final Tuple tuple = Tuple.of(numId, numParentId,numId);
        query.append("WITH old AS (SELECT parent_id FROM explorer.folders WHERE id = $1) ");
        query.append("UPDATE explorer.folders SET parent_id=$2 WHERE id=$3 RETURNING (SELECT parent_id FROM old);");
        return pgPool.preparedQuery(query.toString(), tuple).map(e->{
            final Row row = e.iterator().next();
            final Integer parentId = row.getInteger("parent_id");
            return Optional.ofNullable(parentId);
        });
    }

    protected void beforeCreateOrUpdate(final JsonObject source){
        if(source.getValue("parentId") instanceof String){
            source.put("parentId", Integer.valueOf(source.getValue("parentId").toString()));
        }
    }

    protected Object toSqlId(final String id) {
        return Integer.valueOf(id);
    }


    public Future<Map<String, List<String>>> getAncestors(final Set<Integer> ids) {
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final Tuple inTuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        final StringBuilder query = new StringBuilder();
        query.append("WITH RECURSIVE ancestors(id,name, parent_id) AS ( ");
        query.append(String.format("   SELECT f1.id, f1.name, f1.parent_id FROM explorer.folders f1 WHERE f1.id IN (%s) ", inPlaceholder));
        query.append("   UNION ALL ");
        query.append("   SELECT f2.id, f2.name, f2.parent_id FROM explorer.folders f2, ancestors  WHERE f2.id = ancestors.parent_id ");
        query.append(") ");
        query.append("SELECT * FROM ancestors;");
        return pgPool.preparedQuery(query.toString(), inTuple).map(ancestors -> {
            final Map<Integer, Integer> parentById = new HashMap<>();
            for (final Row row : ancestors) {
                //get parent of each
                final Integer id = row.getInteger("id");
                final Integer parent_id = row.getInteger("parent_id");
                parentById.put(id, parent_id);
            }
            //then get ancestors of each by recursion
            final Map<String, List<String>> map = new HashMap<>();
            for (final Integer id : ids) {
                if(id != null){
                    final List<Integer> ancestorIds = getAncestors(parentById, id);
                    final List<Integer> ancestorsSafe = ancestorIds.stream().filter(e -> e!= null).collect(Collectors.toList());
                    map.put(id.toString(), ancestorsSafe.stream().map(e -> e.toString()).collect(Collectors.toList()));
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
}
