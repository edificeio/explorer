package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.DefaultMuteService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Request;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.impl.ExplorerPlugin;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;

@RunWith(VertxUnitRunner.class)
public class IngestJobTestRedis extends IngestJobTest {
    private static JsonObject redisConfig;
    private IngestJob job;
    private PostgresClient postgresClient;
    private ExplorerPlugin explorerPlugin;
    private ShareTableManager shareTableManager;
    private ResourceService resourceService;
    private JsonObject postgresqlConfig;
    private RedisClient redisClient;
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine")).withExposedPorts(6379);

    @BeforeClass
    public static void setupAll(TestContext context){
        final Async async = context.async();
        new RedisClient(test.vertx(),getRedisConfig()).getClient().flushall(new ArrayList<>(), e -> {
            async.complete();
        });
    }

    protected JsonObject getPostgresConfig(){
        if(postgresqlConfig == null){
            postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        }
        return postgresqlConfig;
    }

    protected IPostgresClient getPostgresClient(){
        if(postgresClient == null) {
            postgresClient = new PostgresClient(test.vertx(), getPostgresConfig());
        }
        return postgresClient;
    }
    protected static JsonObject getRedisConfig(){
        if(redisConfig == null){
            redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        }
        return redisConfig;
    }

    protected RedisClient getRedisClient(){
        if(redisClient == null) {
            redisClient = new RedisClient(test.vertx(), getRedisConfig());
        }
        return redisClient;
    }

    @Override
    protected synchronized IngestJob getIngestJob() {
        if (job == null) {
            final MessageReader reader = MessageReader.redis(test.vertx(), getRedisClient(), new JsonObject());
            final JsonObject jobConfig = new JsonObject().put("opensearch-options", new JsonObject().put("wait-for", true));
            job = IngestJob.createForTest(test.vertx(), elasticClientManager,getPostgresClient(), jobConfig, reader);
        }
        return job;
    }

    @Override
    protected ExplorerPlugin getExplorerPlugin() {
        if(explorerPlugin == null){
            explorerPlugin = FakePostgresPlugin.withRedisStream(test.vertx(), getRedisClient(), getPostgresClient());
        }
        return explorerPlugin;
    }

    @Override
    public ResourceService getResourceService() {
        if(resourceService == null){
            final IExplorerPluginCommunication comm = getExplorerPlugin().getCommunication();
            final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
            resourceService = new ResourceServiceElastic(elasticClientManager, getShareTableManager(), comm, getPostgresClient(), muteService);
        }
        return resourceService;
    }

    @Override
    public ShareTableManager getShareTableManager() {
        if(shareTableManager == null){
            shareTableManager = new DefaultShareTableManager();
        }
        return shareTableManager;
    }
}
