package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.explorer.impl.ExplorerPluginResourceSql;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FolderExplorerPlugin extends ExplorerPluginResourceSql {
    protected final FolderExplorerDbSql dbHelper;
    public FolderExplorerPlugin(final IExplorerPluginCommunication communication, final PostgresClient pgClient) {
        super(communication, pgClient.getClientPool());
        this.dbHelper = new FolderExplorerDbSql(pgClient);
    }

    public static FolderExplorerPlugin create(final Vertx vertx, final JsonObject config, final PostgresClient postgres) throws Exception {
        if(config.getString("stream", "redis").equalsIgnoreCase("redis")){
            final RedisClient redis = RedisClient.create(vertx, config);
            return withRedisStream(vertx, redis, postgres);
        }else{
            return withPgStream(vertx, postgres);
        }
    }

    public static FolderExplorerPlugin withPgStream(final Vertx vertx, final PostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgres);
        return new FolderExplorerPlugin(communication, postgres);
    }

    public static FolderExplorerPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final PostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis);
        return new FolderExplorerPlugin(communication, postgres);
    }

    public final Future<Void> update(final UserInfos user, final String id, final JsonObject source){
        return dbHelper.update(id, source).compose(e->{
            setIdForModel(source, e.id.toString());
            return notifyUpsert(user, source);
        });
    }

    public final Future<Optional<JsonObject>> get(final UserInfos user, final String id){
        return this.getByIds(new HashSet<>(Arrays.asList(id))).map(e->{
            if(e.isEmpty()){
                return Optional.empty();
            }else{
                return Optional.of(e.get(0));
            }
        });
    }

    public final Future<List<JsonObject>> get(final UserInfos user, final Collection<String> id){
        final Set<String> ids = new HashSet<>(id);
        return this.getByIds(ids).map(e->{
            return e;
        });
    }

    public final Future<Void> move(final UserInfos user, final String id, final Optional<String> newParent){
        return dbHelper.move(id, newParent).compose(move->{
            final Optional<Integer> oldParent = move.parentId;
            final List<JsonObject> sources = new ArrayList<>();
            final JsonObject source = new JsonObject();
            if(move.application.isPresent()){
                source.put("application", move.application.get());
            }
            //add
            sources.add(setIdForModel(source.copy(), id));
            //update children of oldParent
            if(oldParent.isPresent()){
                sources.add(setIdForModel(source.copy(), oldParent.get().toString()));
            }
            //update children of newParent
            if(newParent.isPresent()){
                sources.add(setIdForModel(source.copy(), newParent.get().toString()));
            }
            return notifyUpsert(user, sources);
        });
    }

    public final Future<Void> move(final UserInfos user, final Collection<String> idStr, final Optional<String> newParent){
        final Collection<Integer> ids = idStr.stream().map(e -> Integer.valueOf(e)).collect(Collectors.toSet());
        return dbHelper.move(ids, newParent).compose(oldParent->{
            final List<JsonObject> sources = new ArrayList<>();
            for(final Integer key : oldParent.keySet()){
                final FolderExplorerDbSql.FolderMoveResult move = oldParent.get(key);
                final Optional<Integer> parentOpt = move.parentId;
                final JsonObject source = new JsonObject();
                if(move.application.isPresent()){
                    source.put("application", move.application.get());
                }
                //add
                sources.add(setIdForModel(source.copy(), key.toString()));
                //update children of oldParent
                if(parentOpt.isPresent()){
                    sources.add(setIdForModel(source.copy(), parentOpt.get().toString()));
                }
                //update children of newParent
                if(newParent.isPresent()){
                    sources.add(setIdForModel(source.copy(), newParent.get()));
                }
            }
            return notifyUpsert(user, sources);
        });
    }

    @Override
    protected String getApplication() {
        return ExplorerConfig.FOLDER_APPLICATION;
    }
    @Override
    protected Object toSqlId(final String id) {
        return Integer.valueOf(id);
    }
    @Override
    protected String getResourceType() {
        return ExplorerConfig.FOLDER_TYPE;
    }
    @Override
    protected String getTableName() { return this.dbHelper.getTableName(); }
    @Override
    protected List<String> getColumns() { return this.dbHelper.getColumns(); }
    @Override
    protected List<String> getMessageFields() { return Arrays.asList("name", "application", "resourceType", "parentId", "creator_id", "creator_name"); }

    @Override
    protected Future<ExplorerMessage> toMessage(final ExplorerMessage message, final JsonObject source) {
        return Future.succeededFuture(transform(message, source));
    }
    @Override
    protected Future<List<ExplorerMessage>> toMessage(final List<JsonObject> sources, final Function<JsonObject, ExplorerMessage> builder) {
        final List<ExplorerMessage> messages = sources.stream().map(e->{
            return transform(builder.apply(e), e);
        }).collect(Collectors.toList());
        return Future.succeededFuture(messages);
    }

    @Override
    public Future<List<String>> doCreate(final UserInfos user, final List<JsonObject> sources, final boolean isCopy) {
        for(final JsonObject source : sources){
            dbHelper.beforeCreateOrUpdate(source);
        }
        return super.doCreate(user, sources, isCopy);
    }

    protected ExplorerMessage transform(final ExplorerMessage message, final JsonObject object) {
        //force application
        if(object.containsKey("application")){
            message.withForceApplication(object.getString("application"));
        }
        if(object.containsKey("name")){
            message.withName(object.getString("name"));
        }
        message.withTrashed(object.getBoolean("trashed", false));
        final Optional<Long> parentId = Optional.ofNullable(object.getValue("parentId")).map(e->{
            return Long.valueOf(e.toString());
        });
        message.withParentId(parentId);
        return message;
    }


}
