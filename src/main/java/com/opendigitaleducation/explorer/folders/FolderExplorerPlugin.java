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
            return notifyUpsert(user, source);
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
        //get descendants
        final Future<Map<String, List<String>>> ancestorsF = getAncestors(ids);
        // get children ids....
        final Future<Map<String, List<String>>> childrenF = getChildrenIds(idsAndParents);
        return CompositeFuture.all(ancestorsF, childrenF).map(e -> {
            final Map<String, List<String>> ancestors = ancestorsF.result();
            final Map<String, List<String>> children = childrenF.result();
            //Transform all
            final List<ExplorerMessage> messages = new ArrayList<>();
            for (final JsonObject source : sources) {
                final String id = source.getInteger("id").toString();
                source.put("childrenIds", new JsonArray(children.getOrDefault(id, new ArrayList<>())));
                source.put("ancestors", new JsonArray(ancestors.getOrDefault(id, new ArrayList<>())));
                final ExplorerMessage mess = builder.apply(source);
                messages.add(transform(mess, source));
            }
            //update parent (childrenIds)
            for(final Integer parentId : parentIds){
                final JsonObject source = new JsonObject();
                setIdForModel(source, parentId.toString());
                //set childrenIds
                final JsonObject custom = new JsonObject();
                custom.put("childrenIds", new JsonArray(children.getOrDefault(parentId.toString(), new ArrayList<>())));
                final ExplorerMessage mess = builder.apply(source);
                mess.withCustomFields(custom);
                messages.add(mess);
            }
            return messages;
        });
    }

    protected Future<Map<String, List<String>>> getAncestors(final Set<Integer> ids) {
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

    protected Future<Map<String, List<String>>> getChildrenIds(final Set<Integer> ids) {
        final String inPlaceholder = PostgresClient.inPlaceholder(ids, 1);
        final Tuple inTuple = PostgresClient.inTuple(Tuple.tuple(), ids);
        final String query = String.format("SELECT f1.id, f1.parent_id FROM explorer.folders f1 WHERE f1.parent_id IN (%s) ", inPlaceholder);
        return pgPool.preparedQuery(query.toString(), inTuple).map(ancestors -> {
            final Map<String, List<String>> childrenById = new HashMap<>();
            for (final Row row : ancestors) {
                //get parent of each
                final Integer id = row.getInteger("id");
                final Integer parent_id = row.getInteger("parent_id");
                childrenById.putIfAbsent(parent_id.toString(), new ArrayList<>());
                childrenById.get(parent_id.toString()).add(id.toString());
            }
            return childrenById;
        });
    }

    protected List<Integer> getAncestors(final Map<Integer, Integer> parentById, final Integer root) {
        final Integer parent = parentById.get(root);
        final List<Integer> all = new ArrayList<>();
        if (parent != null) {
            final List<Integer> ancestors = getAncestors(parentById, parent);
            all.addAll(ancestors);
        }
        all.add(parent);
        return all;
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
        message.withOverrideFields(customFields);
        return message;
    }
}
