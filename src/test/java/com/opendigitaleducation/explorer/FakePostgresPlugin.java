package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.plugin.*;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.redis.RedisClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

public class FakePostgresPlugin extends ExplorerPluginResourceCrud {
    public static final String FAKE_APPLICATION = "test";
    public static final String FAKE_TYPE = "fake";
    static Logger log = LoggerFactory.getLogger(FakePostgresPlugin.class);

    protected FakePostgresPlugin(final ExplorerPluginCommunication communication, final PostgresClient pgClient) {
        super(communication, new FakeExplorerCrud(pgClient));
    }

    public static FakePostgresPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final PostgresClient postgres) {
        final ExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis);
        return new FakePostgresPlugin(communication, postgres);
    }

    public static FakePostgresPlugin withPostgresChannel(final Vertx vertx, final PostgresClient postgres) {
        final ExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgres);
        return new FakePostgresPlugin(communication, postgres);
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

    static class FakeExplorerCrud extends ExplorerResourceCrudSql{

        public FakeExplorerCrud(final PostgresClient pgClient) {
            super(pgClient.getClientPool());
        }

        @Override
        protected String getTableName() { return "explorer.test_fake"; }

        @Override
        protected List<String> getColumns() { return Arrays.asList("name", "creator_id", "creator_name"); }

    }

}
