package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunication;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunicationRedis;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginResourceCrud;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientPool;
import com.opendigitaleducation.explorer.redis.RedisClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FolderExplorerPlugin extends ExplorerPluginResourceCrud {
    protected final PostgresClientPool pgPool;

    public FolderExplorerPlugin(final ExplorerPluginCommunication communication, final PostgresClient pgClient) {
        super(communication, new FolderExplorerCrudSql(pgClient));
        this.pgPool = pgClient.getClientPool();
    }

    public static FolderExplorerPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final PostgresClient postgres) {
        final ExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis);
        return new FolderExplorerPlugin(communication, postgres);
    }

    public final Future<Void> update(final UserInfos user, final String id, final JsonObject source){
        return ((FolderExplorerCrudSql)resourceCrud).update(id, source).compose(e->{
            setIdForModel(source, e.id.toString());
            return notifyUpsert(user, source);
        });
    }

    public final Future<Optional<JsonObject>> get(final UserInfos user, final String id){
        return resourceCrud.getByIds(new HashSet<>(Arrays.asList(id))).map(e->{
            if(e.isEmpty()){
                return Optional.empty();
            }else{
                return Optional.of(e.get(0));
            }
        });
    }


    public final Future<Void> move(final UserInfos user, final String id, final Optional<String> newParent){
        return ((FolderExplorerCrudSql)resourceCrud).move(id, newParent).compose(oldParent->{
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
