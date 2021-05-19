package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ingest.MessageIngester;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.plugin.ExplorerResourceCrudSql;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import fr.wseduc.webutils.security.Md5;
import io.reactiverse.pgclient.PgRowSet;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class FolderExplorerCrudSql extends ExplorerResourceCrudSql {

    public FolderExplorerCrudSql(final PostgresClient pool) {
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

    public final Future<Void> update(final String id, final JsonObject source){
        beforeCreateOrUpdate(source);
        final Tuple tuple = Tuple.tuple();
        tuple.addValue(id);
        final String updatePlaceholder = PostgresClient.updatePlaceholders(source, 2, getColumns(),tuple);
        final String queryTpl = "UPDATE %s SET %s WHERE id = $1";
        final String query = String.format(queryTpl, getTableName(), updatePlaceholder);
        return pgPool.preparedQuery(query, tuple).mapEmpty();
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

    public Future<Map<Integer, ExplorerMessage>> deleteTemporarlyResources(final Collection<? extends ExplorerMessage> resources){
        if(resources.isEmpty()){
            return Future.succeededFuture(new HashMap<>());
        }
        final Set<String> uniqIds = resources.stream().map(e->e.getResourceUniqueId()).collect(Collectors.toSet());
        final Tuple tuple = PostgresClient.inTuple(Tuple.tuple(), uniqIds);
        final String inPlaceholder = PostgresClient.inPlaceholder(uniqIds, 1);
        final String queryTpl = "UPDATE FROM explorer.resources WHERE resource_unique_id IN (%s) RETURNING *";
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
        final String queryTpl = "DELETE FROM explorer.resources WHERE resource_unique_id IN (%s) RETURNING *";
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
        final List<JsonObject> resourcesJson = resources.stream().map(e->{
            final String resourceUniqueId = e.getResourceUniqueId();
            return new JsonObject().put("ent_id", e.getId()).put("application",e.getApplication())
                    .put("resource_type", e.getResourceType()).put("resource_unique_id", resourceUniqueId)
                    .put("creator_id", e.getCreatorId());
        }).collect(Collectors.toList());
        final Map<String, Object> defaultVal = new HashMap<>();
        defaultVal.put("name", "");
        final Tuple tuple = PostgresClient.insertValues(resourcesJson, Tuple.tuple(), defaultVal, "ent_id", "name","application","resource_type","resource_unique_id", "creator_id");
        final String insertPlaceholder = PostgresClient.insertPlaceholders(resourcesJson, 1, "ent_id", "name","application","resource_type", "resource_unique_id", "creator_id");
        final StringBuilder queryTpl = new StringBuilder();
        queryTpl.append("WITH ( ");
        queryTpl.append("  INSERT INTO explorer.resources as r (ent_id, name,application,resource_type, resource_unique_id) ");
        queryTpl.append("  VALUES %s ON CONFLICT(resource_unique_id) DO UPDATE SET name=r.name RETURNING * ");
        queryTpl.append(") AS upserted ");
        queryTpl.append("SELECT fr.resource_id as resource_id, fr.folder_id as folder_id, ");
        queryTpl.append("       fr.user_id as user_id,ent_id,resource_unique_id, creator_id ");
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
                results.putIfAbsent(id, new ResouceSql(entId, id, resourceUniqueId, creatorId));
                results.get(id).folders.add(new FolderSql(folderId, userId));
            }
            return new ArrayList<>(results.values());
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

        public ResouceSql(String entId, Integer id, String resourceUniqId, String creatorId) {
            this.entId = entId;
            this.id = id;
            this.creatorId = creatorId;
            this.resourceUniqId = resourceUniqId;
        }
        public final String creatorId;
        //list of all folders
        public final List<FolderSql> folders = new ArrayList<>();
        public final String entId;
        public final Integer id;
        public final String resourceUniqId;
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
