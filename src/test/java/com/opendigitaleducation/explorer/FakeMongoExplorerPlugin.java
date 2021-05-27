package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.plugin.*;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.redis.RedisClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;

public class FakeMongoExplorerPlugin extends ExplorerPluginResourceCrud {
    public static final String FAKE_APPLICATION = "test";
    public static final String FAKE_TYPE = "fake";
    static Logger log = LoggerFactory.getLogger(FakeMongoExplorerPlugin.class);

    protected FakeMongoExplorerPlugin(final ExplorerPluginCommunication communication, final MongoClient mongoClient) {
        super(communication, new FakeMongoExplorerCrud(mongoClient));
    }

    public static FakeMongoExplorerPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final MongoClient mongoClient) {
        final ExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis);
        return new FakeMongoExplorerPlugin(communication, mongoClient);
    }

    public static FakeMongoExplorerPlugin withPostgresChannel(final Vertx vertx, final PostgresClient postgresClient, final MongoClient mongoClient) {
        final ExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgresClient);
        return new FakeMongoExplorerPlugin(communication, mongoClient);
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

    static class FakeMongoExplorerCrud extends ExplorerResourceCrudMongo{

        public FakeMongoExplorerCrud(final MongoClient mongoClient) {
            super(mongoClient);
        }

        @Override
        protected String getCollectionName() { return "explorer.test_fake"; }

    }

}
