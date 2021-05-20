package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunication;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunicationRedis;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginResourceCrud;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientPool;
import com.opendigitaleducation.explorer.redis.RedisClient;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;
import static com.opendigitaleducation.explorer.folders.FolderExplorerCrudSql.FolderRelationship;

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
    public Future<Void> notifyUpsert(UserInfos user, JsonObject source) {
        //force one upsert to upsert multiple message (update parent with children
        return super.notifyUpsert(user, Arrays.asList(source));
    }
    @Override
    public Future<Void> notifyDelete(UserInfos user, JsonObject source) {
        //force one upsert to upsert multiple message (update parent with children
        return super.notifyDelete(user, Arrays.asList(source));
    }

    @Override
    protected Future<ExplorerMessage> toMessage(final ExplorerMessage message, final JsonObject source) {
        return toMessage(Arrays.asList(source), e -> {
            return message;
        }).map(message);
    }

    @Override
    protected Future<List<ExplorerMessage>> toMessage(final List<JsonObject> sources, final Function<JsonObject, ExplorerMessage> builder) {
        final Set<Integer> ids = sources.stream().map(e -> e.getInteger("id")).collect(Collectors.toSet());
        //get parentIds
        final Set<Integer> parentIds = sources.stream().filter(e->e.containsKey("parentId")).map(e->e.getInteger("parentId")).collect(Collectors.toSet());
        final Set<Integer> idsAndParents = new HashSet<>();
        idsAndParents.addAll(ids);
        idsAndParents.addAll(parentIds);
        //get ancestors of each documents
        final Future<Map<String, List<String>>> ancestorsF = ((FolderExplorerCrudSql)resourceCrud).getAncestors(ids);
        // get parent/child relationship for folder and their parents
        final Future<Map<String, FolderRelationship>> relationsF = ((FolderExplorerCrudSql)resourceCrud).getRelationships(idsAndParents);
        return CompositeFuture.all(ancestorsF, relationsF).map(e -> {
            final Map<String, List<String>> ancestors = ancestorsF.result();
            final Map<String, FolderRelationship> relations = relationsF.result();
            //Transform all
            final List<ExplorerMessage> messages = new ArrayList<>();
            for (final JsonObject source : sources) {
                final String id = source.getInteger("id").toString();
                final FolderRelationship relation = relations.get(id);
                source.put("childrenIds", new JsonArray(relation.childrenIds));
                if(relation.parentId.isPresent()){
                    source.put("parentId", relation.parentId.get());
                }
                source.put("ancestors", new JsonArray(ancestors.getOrDefault(id, new ArrayList<>())));
                final ExplorerMessage mess = builder.apply(source);
                messages.add(transform(mess, source));
            }
            //update parent (childrenIds)
            for(final Integer parentId : parentIds){
                final String parentIdStr = parentId.toString();
                final JsonObject source = setIdForModel(new JsonObject(), parentId.toString());
                //set childrenIds
                final FolderExplorerCrudSql.FolderRelationship relation = relations.get(parentIdStr);
                source.put("childrenIds", new JsonArray(relation.childrenIds));
                if(relation.parentId.isPresent()){
                    source.put("parentId", relation.parentId.get());
                }
                //recompute ancestors from child ancestors
                if(!relation.childrenIds.isEmpty()){
                    //get child ancestors
                    final String child = relation.childrenIds.get(0);
                    final List<String> parentAncestors = new ArrayList<>(ancestors.getOrDefault(child, new ArrayList<>()));
                    //remove self from child ancestors
                    parentAncestors.remove(parentIdStr);
                    source.put("ancestors", new JsonArray(parentAncestors));
                    //add parent to list of updated folders
                    final ExplorerMessage mess = builder.apply(source);
                    //update parent only if childrenids has changed
                    messages.add(transform(mess, source));
                }
            }
            return messages;
        });
    }

    protected ExplorerMessage transform(final ExplorerMessage message, final JsonObject object) {
        message.withName(object.getString("name"));
        message.withTrashed(object.getBoolean("trashed", false));
        final JsonObject customFields = new JsonObject();
        if(object.containsKey("parentId")){
            customFields.put("parentId", object.getValue("parentId").toString());
        }else{
            customFields.put("parentId", ExplorerConfig.ROOT_FOLDER_ID);
        }
        //MOVE
        customFields.put("childrenIds", object.getJsonArray("childrenIds", new JsonArray()));
        final JsonArray ancestors = object.getJsonArray("ancestors", new JsonArray());
        if(!ancestors.contains(ExplorerConfig.ROOT_FOLDER_ID)){
            //prepend root
            final JsonArray newAncestors = new JsonArray();
            newAncestors.add(ExplorerConfig.ROOT_FOLDER_ID);
            newAncestors.addAll(ancestors.copy());
            ancestors.clear();
            ancestors.addAll(newAncestors);
        }
        customFields.put("ancestors", ancestors);
        //END MOVE
        message.withOverrideFields(customFields);
        return message;
    }
}
