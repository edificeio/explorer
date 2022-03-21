package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.explorer.impl.ExplorerPluginResourceDb;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.postgres.PostgresClientPool;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FolderExplorerPlugin extends ExplorerPluginResourceDb {
    protected final PostgresClientPool pgPool;

    public FolderExplorerPlugin(final IExplorerPluginCommunication communication, final PostgresClient pgClient) {
        super(communication, new FolderExplorerDbSql(pgClient));
        this.pgPool = pgClient.getClientPool();
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
        return ((FolderExplorerDbSql)explorerDb).update(id, source).compose(e->{
            setIdForModel(source, e.id.toString());
            return notifyUpsert(user, source);
        });
    }

    public final Future<Optional<JsonObject>> get(final UserInfos user, final String id){
        return explorerDb.getByIds(new HashSet<>(Arrays.asList(id))).map(e->{
            if(e.isEmpty()){
                return Optional.empty();
            }else{
                return Optional.of(e.get(0));
            }
        });
    }

    public final Future<List<JsonObject>> get(final UserInfos user, final Collection<String> id){
        final Set<String> ids = new HashSet<>(id);
        return explorerDb.getByIds(ids).map(e->{
            return e;
        });
    }


    public final Future<Void> move(final UserInfos user, final String id, final Optional<String> newParent){
        return ((FolderExplorerDbSql)explorerDb).move(id, newParent).compose(oldParent->{
            final List<JsonObject> sources = new ArrayList<>();
            sources.add(setIdForModel(new JsonObject(), id));
            //update children of oldParent
            if(oldParent.isPresent()){
                sources.add(setIdForModel(new JsonObject(), oldParent.get().toString()));
            }
            //update children of newParent
            if(newParent.isPresent()){
                sources.add(setIdForModel(new JsonObject(), newParent.get().toString()));
            }
            return notifyUpsert(user, sources);
        });
    }

    public final Future<Void> move(final UserInfos user, final Collection<String> idStr, final Optional<String> newParent){
        final Collection<Integer> ids = idStr.stream().map(e -> Integer.valueOf(e)).collect(Collectors.toSet());
        return ((FolderExplorerDbSql)explorerDb).move(ids, newParent).compose(oldParent->{
            final List<JsonObject> sources = new ArrayList<>();
            for(final Integer key : oldParent.keySet()){
                sources.add(setIdForModel(new JsonObject(), key.toString()));
                final Optional<Integer> parentOpt = oldParent.get(key);
                //update children of oldParent
                if(parentOpt.isPresent()){
                    sources.add(setIdForModel(new JsonObject(), parentOpt.get().toString()));
                }
                //update children of newParent
                if(newParent.isPresent()){
                    sources.add(setIdForModel(new JsonObject(), newParent.get()));
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
    protected String getResourceType() {
        return ExplorerConfig.FOLDER_TYPE;
    }

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

    protected ExplorerMessage transform(final ExplorerMessage message, final JsonObject object) {
        message.withName(object.getString("name"));
        message.withTrashed(object.getBoolean("trashed", false));
        final JsonObject override = new JsonObject();
        if(object.containsKey("parentId")){
            override.put("parentId", object.getValue("parentId").toString());
        }
        message.withOverrideFields(override);
        return message;
    }
}
