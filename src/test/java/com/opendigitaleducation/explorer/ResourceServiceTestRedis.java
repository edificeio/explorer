package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.redis.RedisClient;
import com.opendigitaleducation.explorer.services.ExplorerService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ExplorerServicePostgres;
import com.opendigitaleducation.explorer.services.impl.ExplorerServiceRedis;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.PostgresShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Request;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@RunWith(VertxUnitRunner.class)
public class ResourceServiceTestRedis extends ResourceServiceTest {
    private static JsonObject redisConfig;
    private IngestJob job;
    private PostgresClient postgresClient;
    private ExplorerService explorerService;
    private ShareTableManager shareTableManager;
    private ResourceService resourceService;
    private JsonObject postgresqlConfig;
    private RedisClient redisClient;
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(("redis:5.0.3-alpine")).withReuse(true);

    @BeforeClass
    public static void setup(TestContext context){
        final Async async = context.async();
        new RedisClient(test.vertx(),getRedisConfig()).getClient().send(Request.cmd(Command.FLUSHALL), e -> {
            async.complete();
        });
    }

    protected JsonObject getPostgresConfig(){
        if(postgresqlConfig == null){
            postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        }
        return postgresqlConfig;
    }

    protected PostgresClient getPostgresClient(){
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
            final MessageReader reader = MessageReader.redis(getRedisClient(), new JsonObject());
            job = IngestJob.create(test.vertx(), getResourceService(), new JsonObject(), reader);
        }
        return job;
    }

    @Override
    protected ExplorerService getExplorerService() {
        if(explorerService == null){
            explorerService = new ExplorerServiceRedis(test.vertx(), getRedisClient());
        }
        return explorerService;
    }

    @Override
    public ResourceService getResourceService() {
        if(resourceService == null){
            resourceService = new ResourceServiceElastic(elasticClientManager, getShareTableManager(), esIndex);
        }
        return resourceService;
    }

    @Override
    public ShareTableManager getShareTableManager() {
        if(shareTableManager == null){
            shareTableManager = new PostgresShareTableManager(getPostgresClient());
        }
        return shareTableManager;
    }
}
