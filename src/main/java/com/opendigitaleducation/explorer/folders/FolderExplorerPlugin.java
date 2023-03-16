package com.opendigitaleducation.explorer.folders;

import com.opendigitaleducation.explorer.ExplorerConfig;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.*;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.explorer.impl.ExplorerPluginResourceSql;
import org.entcore.common.explorer.impl.ExplorerSubResource;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.share.ShareService;
import org.entcore.common.user.UserInfos;

import java.util.*;

import static java.util.Collections.emptyList;

import static org.entcore.common.explorer.ExplorerPluginMetricsFactory.getExplorerPluginMetricsRecorder;

public class FolderExplorerPlugin extends ExplorerPluginResourceSql {
    protected final FolderExplorerDbSql dbHelper;
    public FolderExplorerPlugin(final IExplorerPluginCommunication communication, final IPostgresClient pgClient) {
        super(communication, pgClient);
        this.dbHelper = new FolderExplorerDbSql(pgClient);
    }

    public FolderExplorerDbSql getDbHelper() {return dbHelper;}

    @Override
    protected Map<String, SecuredAction> getSecuredActions() {
        return new HashMap<>();
    }

    @Override
    protected Optional<ShareService> getShareService() {
        return Optional.empty();
    }

    @Override
    public JsonObject setIdForModel(JsonObject json, String id) {
        return super.setIdForModel(json, id);
    }


    public static FolderExplorerPlugin create() throws Exception {
        final IExplorerPlugin plugin =  ExplorerPluginFactory.createPostgresPlugin((params)->{
            return new FolderExplorerPlugin(params.getCommunication(), params.getDb());
        });
        return (FolderExplorerPlugin) plugin;
    }

    public static FolderExplorerPlugin create(final Vertx vertx, final JsonObject config, final IPostgresClient postgres) throws Exception {
        if(config.getString("stream", "redis").equalsIgnoreCase("redis")){
            final RedisClient redis = RedisClient.create(vertx, config);
            return withRedisStream(vertx, redis, postgres);
        }else{
            return withPgStream(vertx, postgres);
        }
    }

    public static FolderExplorerPlugin withPgStream(final Vertx vertx, final IPostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgres, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        return new FolderExplorerPlugin(communication, postgres);
    }

    public static FolderExplorerPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final IPostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        return new FolderExplorerPlugin(communication, postgres);
    }

    public final Future<Void> update(final UserInfos user, final String id, final JsonObject source){
        setIngestJobState(source, IngestJobState.TO_BE_SENT);
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
    protected List<String> getMessageFields() {
        // TODO JBER voir à quoi sert ce truc
        return Arrays.asList("name", "application", "resourceType", "parentId", "creator_id", "creator_name", "version", "ingest_job_state");
    }

    @Override
    protected Future<ExplorerMessage> doToMessage(final ExplorerMessage message, final JsonObject source) {
        return Future.succeededFuture(transform(message, source));
    }

    @Override
    protected List<ExplorerSubResource> getSubResourcesPlugin() {
        return emptyList();
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
