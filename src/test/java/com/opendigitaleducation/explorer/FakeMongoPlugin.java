package com.opendigitaleducation.explorer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.impl.ExplorerDbMongo;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.explorer.impl.ExplorerPluginResourceDb;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;

public class FakeMongoPlugin extends ExplorerPluginResourceDb {
    public static final String FAKE_APPLICATION = "test";
    public static final String FAKE_TYPE = "fake";
    public static final String COLLECTION = "explorer.test_fake";
    static Logger log = LoggerFactory.getLogger(FakeMongoPlugin.class);

    protected FakeMongoPlugin(final IExplorerPluginCommunication communication, final MongoClient mongoClient) {
        super(communication, new FakeMongoExplorerCrud(mongoClient));
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
        return Future.succeededFuture(message);
    }

    static class FakeMongoExplorerCrud extends ExplorerDbMongo {

        public FakeMongoExplorerCrud(final MongoClient mongoClient) {
            super(mongoClient);
        }

        @Override
        protected String getCollectionName() { return COLLECTION; }

    }

}
