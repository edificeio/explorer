package com.opendigitaleducation.explorer.plugin;

import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientPool;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public abstract class ExplorerResourceCrudSql implements ExplorerResourceCrud {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final PostgresClientPool pgPool;

    public ExplorerResourceCrudSql(final PostgresClientPool pool){
        this.pgPool = pool;
    }

    @Override
    public void fetchByDate(final ExplorerStream<JsonObject> stream, final Optional<Date> from, final Optional<Date> to) {
        //TODO cursor and filter by date
        pgPool.preparedQuery(String.format("SELECT * FROM %s ", getTableName()), Tuple.tuple()).onSuccess(rows->{
            final List<JsonObject> jsons = new ArrayList<>();
            for(final Row row : rows){
                jsons.add(PostgresClient.toJson(row, rows));
            }
            stream.end(jsons);
        }).onFailure(e->{
            stream.end();
            log.error("Failed to fetch folders for index: ", e.getCause());
        });
    }


    @Override
    public Future<List<String>> createAll(final UserInfos user, final List<JsonObject> sources) {
        final Map<String, Object> map = new HashMap<>();
        map.put(getCreatorIdColumn(), user.getUserId());
        map.put(getCreatorNameColumn(), user.getUsername());
        final String inPlaceholder = PostgresClient.insertPlaceholders(sources, 1, getColumns());
        final Tuple inValues = PostgresClient.insertValuesWithDefault(sources, Tuple.tuple(), map, getMessageFields());
        final String queryTpl = "INSERT INTO %s(%s) VALUES %s returning id";
        final String columns = String.join(",", getColumns());
        final String query = String.format(queryTpl,getTableName(),columns, inPlaceholder);
        return pgPool.preparedQuery(query, inValues).map(result -> {
            final List<String> ids = new ArrayList<>();
            for (final Row row : result) {
                ids.add(row.getInteger(0) + "");
            }
            return ids;
        });
    }

    @Override
    public Future<List<Boolean>> deleteById(final List<String> ids) {
        final String queryTpl = String.format("DELETE FROM %s WHERE id IN (%s);");
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final String query = String.format(queryTpl, getTableName(),inPlaceholder);
        final Tuple tuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        return pgPool.preparedQuery(query, tuple).map(result -> {
            return ids.stream().map(e -> true).collect(Collectors.toList());
        });
    }

    @Override
    public String getIdForModel(final JsonObject json) {
        return json.getInteger(getIdColumn()) + "";
    }

    @Override
    public void setIdForModel(final JsonObject json, final String id) { json.put(getIdColumn(), Integer.valueOf(id)); }

    @Override
    public UserInfos getCreatorForModel(final JsonObject json) {
        final String id = json.getString(getCreatorIdColumn());
        final String name = json.getString(getCreatorNameColumn());
        final UserInfos user = new UserInfos();
        user.setUserId(id);
        user.setUsername(name);
        return user;
    }

    protected String getCreatorIdColumn(){ return "creator_id"; }

    protected String getCreatorNameColumn(){ return "creator_name"; }

    protected String getIdColumn(){ return "id"; }

    protected abstract String getTableName();

    protected abstract List<String> getColumns();

    protected List<String> getMessageFields(){
        return getColumns();
    }

}
