package com.opendigitaleducation.explorer;

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
import org.entcore.common.explorer.impl.ExplorerPluginResourceSql;
import org.entcore.common.explorer.impl.ExplorerSubResource;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.share.ShareRoles;
import org.entcore.common.share.ShareService;

import java.util.*;

import static java.util.Collections.emptyList;

public class FakePostgresPlugin extends ExplorerPluginResourceSql {
    public static final String FAKE_APPLICATION = "test";
    public static final String FAKE_TYPE = "fake";
    static Logger log = LoggerFactory.getLogger(FakePostgresPlugin.class);
    private Map<String, SecuredAction> securedActions = new HashMap<>();

    protected FakePostgresPlugin(final IExplorerPluginCommunication communication, final IPostgresClient pgClient) {
        super(communication, pgClient);
        securedActions.put(ShareRoles.Read.key, new SecuredAction(ShareRoles.Read.key,ShareRoles.Read.key,"resource", "test.read"));
        securedActions.put(ShareRoles.Manager.key, new SecuredAction(ShareRoles.Manager.key,ShareRoles.Manager.key,"resource", "test.manage"));
        securedActions.put(ShareRoles.Contrib.key, new SecuredAction(ShareRoles.Contrib.key,ShareRoles.Contrib.key,"resource", "test.contribute"));
    }

    @Override
    protected Optional<ShareService> getShareService() {
        return Optional.empty();
    }

    public static FakePostgresPlugin withRedisStream(final Vertx vertx, final RedisClient redis, final IPostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationRedis(vertx, redis, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        return new FakePostgresPlugin(communication, postgres);
    }

    public static FakePostgresPlugin withPostgresChannel(final Vertx vertx, final IPostgresClient postgres) {
        final IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(vertx, postgres, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        return new FakePostgresPlugin(communication, postgres);
    }

    @Override
    protected String getApplication() { return FAKE_APPLICATION; }

    @Override
    protected String getResourceType() { return FAKE_TYPE; }

    @Override
    protected Future<ExplorerMessage> doToMessage(final ExplorerMessage message, final JsonObject source) {
        message.withName(source.getString("name"));
        message.withContent(source.getString("content"), ExplorerMessage.ExplorerContentType.Text);
        message.withContent(source.getString("html"), ExplorerMessage.ExplorerContentType.Html);
        if(source.containsKey("rights")){
            message.withShared(new JsonArray(), source.getJsonArray("rights").getList());
        }
        return Future.succeededFuture(message);
    }

    @Override
    protected Map<String, SecuredAction> getSecuredActions() {
        return securedActions;
    }

    @Override
    protected List<ExplorerSubResource> getSubResourcesPlugin() {
        return emptyList();
    }

    @Override
    protected String getTableName() { return "explorer.test_fake"; }

    @Override
    protected List<String> getColumns() { return Arrays.asList("name", "creator_id", "creator_name"); }

}
