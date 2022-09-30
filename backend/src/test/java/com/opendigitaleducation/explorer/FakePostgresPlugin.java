package com.opendigitaleducation.explorer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationRedis;
import org.entcore.common.explorer.impl.ExplorerPluginResourceSql;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;

import java.util.Arrays;
import java.util.List;

public class FakePostgresPlugin extends ExplorerPluginResourceSql {
    public static final String FAKE_APPLICATION = "test";
    public static final String FAKE_TYPE = "fake";
    static Logger log = LoggerFactory.getLogger(FakePostgresPlugin.class);

    protected FakePostgresPlugin(final IExplorerPluginCommunication communication, final PostgresClient pgClient) {
        super(communication, pgClient.getClientPool());
    }

    public static FakePostgresPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final PostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis);
        return new FakePostgresPlugin(communication, postgres);
    }

    public static FakePostgresPlugin withPostgresChannel(final Vertx vertx, final PostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgres);
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
        message.withContent(source.getString("html"), ExplorerMessage.ExplorerContentType.Html);
        return Future.succeededFuture(message);
    }

    @Override
    protected String getTableName() { return "explorer.test_fake"; }

    @Override
    protected List<String> getColumns() { return Arrays.asList("name", "creator_id", "creator_name"); }

}
