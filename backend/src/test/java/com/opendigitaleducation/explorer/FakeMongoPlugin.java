package com.opendigitaleducation.explorer;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.ExplorerPluginMetricsFactory;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.IExplorerPluginMetricsRecorder;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.explorer.impl.ExplorerPluginResourceMongo;
import org.entcore.common.explorer.impl.ExplorerSubResource;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.share.ShareModel;
import org.entcore.common.share.ShareRoles;
import org.entcore.common.share.ShareService;
import org.entcore.common.share.impl.MongoDbShareService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;

public class FakeMongoPlugin extends ExplorerPluginResourceMongo {
    public static final String FAKE_APPLICATION = "test";
    public static final String FAKE_TYPE = "fake";
    public static final String COLLECTION = "explorer.test_fake";
    static Logger log = LoggerFactory.getLogger(FakeMongoPlugin.class);
    private final Vertx vertx;
    private Map<String, SecuredAction> securedActions = new HashMap<>();
    protected FakeMongoPlugin(final IExplorerPluginCommunication communication, final MongoClient mongoClient) {
        super(communication, mongoClient);
        this.vertx = communication.vertx();
        securedActions.put(ShareRoles.Read.key, new SecuredAction(ShareRoles.Read.key,ShareRoles.Read.key,"resource"));
        securedActions.put(ShareRoles.Manager.key, new SecuredAction(ShareRoles.Manager.key,ShareRoles.Manager.key,"resource"));
        securedActions.put(ShareRoles.Contrib.key, new SecuredAction(ShareRoles.Contrib.key,ShareRoles.Contrib.key,"resource"));

    }

    @Override
    protected Optional<ShareService> getShareService() {
        Map<String, List<String>> groupedActions = new HashMap<>();
        final ShareService share = new MongoDbShareService(vertx.eventBus(), MongoDb.getInstance(),COLLECTION,securedActions, groupedActions);
        return Optional.of(share);
    }

    public static FakeMongoPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final MongoClient mongoClient) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        return new FakeMongoPlugin(communication, mongoClient);
    }

    public static FakeMongoPlugin withPostgresChannel(final Vertx vertx, final PostgresClient postgresClient, final MongoClient mongoClient) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgresClient, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        return new FakeMongoPlugin(communication, mongoClient);
    }

    @Override
    protected String getApplication() { return FAKE_APPLICATION; }

    @Override
    protected String getResourceType() { return FAKE_TYPE; }

    @Override
    protected Future<ExplorerMessage> doToMessage(final ExplorerMessage message, final JsonObject source) {
        final ShareModel shareModel =  new ShareModel(source.getJsonArray("shared", new JsonArray()), securedActions, Optional.empty());
        message.withName(source.getString("name"));
        message.withContent(source.getString("content"), ExplorerMessage.ExplorerContentType.Text);
        message.withShared(shareModel);
        if(source.containsKey("rights")){
            message.withShared(new JsonArray(), source.getJsonArray("rights").getList());
        }
        if(source.containsKey("my_flag")) {
            message.getMessage().put("my_flag", source.getString("my_flag"));
        }
        message.getMessage().put("subresources", source.getJsonArray("subresources", new JsonArray()));
        return Future.succeededFuture(message);
    }

    @Override
    public Map<String, SecuredAction> getSecuredActions() {
        return securedActions;
    }

    @Override
    protected String getCollectionName() { return COLLECTION; }

    @Override
    protected List<ExplorerSubResource> getSubResourcesPlugin() {
        return emptyList();
    }
}
