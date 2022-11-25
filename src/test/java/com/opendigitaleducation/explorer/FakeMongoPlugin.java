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
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.explorer.impl.ExplorerPluginResourceMongo;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.share.ShareService;
import org.entcore.common.share.impl.MongoDbShareService;

import java.util.*;

public class FakeMongoPlugin extends ExplorerPluginResourceMongo {
    public static final String FAKE_APPLICATION = "test";
    public static final String FAKE_TYPE = "fake";
    public static final String COLLECTION = "explorer.test_fake";
    static Logger log = LoggerFactory.getLogger(FakeMongoPlugin.class);
    private final Vertx vertx;
    protected FakeMongoPlugin(final IExplorerPluginCommunication communication, final MongoClient mongoClient) {
        super(communication, mongoClient);
        this.vertx = communication.vertx();
    }

    @Override
    protected Optional<ShareService> getShareService() {
        Map<String, SecuredAction> securedActions = new HashMap<>();
        Map<String, List<String>> groupedActions = new HashMap<>();
        securedActions.put(ExplorerConfig.RIGHT_READ, new SecuredAction(ExplorerConfig.RIGHT_READ,ExplorerConfig.RIGHT_READ,"resource"));
        securedActions.put(ExplorerConfig.RIGHT_MANAGE, new SecuredAction(ExplorerConfig.RIGHT_MANAGE,ExplorerConfig.RIGHT_MANAGE,"resource"));
        securedActions.put(ExplorerConfig.RIGHT_CONTRIB, new SecuredAction(ExplorerConfig.RIGHT_CONTRIB,ExplorerConfig.RIGHT_CONTRIB,"resource"));
        final ShareService share = new MongoDbShareService(vertx.eventBus(), MongoDb.getInstance(),COLLECTION,securedActions, groupedActions);
        return Optional.of(share);
    }

    public static FakeMongoPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final MongoClient mongoClient) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis);
        return new FakeMongoPlugin(communication, mongoClient);
    }

    public static FakeMongoPlugin withPostgresChannel(final Vertx vertx, final PostgresClient postgresClient, final MongoClient mongoClient) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgresClient);
        return new FakeMongoPlugin(communication, mongoClient);
    }

    @Override
    protected String getApplication() { return FAKE_APPLICATION; }

    @Override
    protected String getResourceType() { return FAKE_TYPE; }

    @Override
    protected Future<ExplorerMessage> toMessage(final ExplorerMessage message, final JsonObject source) {
        message.withName(source.getString("name"));
        message.withContent(source.getString("content"), ExplorerMessage.ExplorerContentType.Text);
        message.withShared(source.getJsonArray("shared", new JsonArray()));
        if(source.containsKey("my_flag")) {
            message.getMessage().put("my_flag", source.getString("my_flag"));
        }
        return Future.succeededFuture(message);
    }

    @Override
    protected String getCollectionName() { return COLLECTION; }

}
