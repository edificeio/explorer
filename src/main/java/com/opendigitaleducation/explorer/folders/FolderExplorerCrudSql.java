package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.plugin.ExplorerResourceCrudSql;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    protected void beforeCreateOrUpdate(final JsonObject source){
        if(source.getValue("parentId") instanceof String){
            source.put("parentId", Integer.valueOf(source.getValue("parentId").toString()));
        }
    }
}
