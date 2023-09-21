package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.DefaultMuteService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.ExplorerPluginMetricsFactory;
import org.entcore.common.explorer.IdAndVersion;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.Arrays;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static java.lang.System.currentTimeMillis;

@RunWith(VertxUnitRunner.class)
public class ResourceServiceTest {

    private static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static ElasticsearchContainer esContainer = test.database().createOpenSearchContainer().withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine")).withExposedPorts(6379);
    static FakePostgresPlugin plugin;
    static ElasticClientManager elasticClientManager;
    static ResourceService resourceService;
    static IngestJob job;
    static String esIndex;
    static String application;

    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final HttpClientOptions httpOptions = new HttpClientOptions().setDefaultHost(esContainer.getHost()).setDefaultPort(esContainer.getMappedPort(9200));
        final HttpClient httpClient = test.vertx().createHttpClient(httpOptions);
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        IngestJobMetricsRecorderFactory.init(test.vertx(), new JsonObject());
        ExplorerPluginMetricsFactory.init(test.vertx(), new JsonObject());
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        esIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + currentTimeMillis();
        ExplorerConfig.getInstance().setEsIndex(FakePostgresPlugin.FAKE_APPLICATION, esIndex);
        application = FakePostgresPlugin.FAKE_APPLICATION;
        System.out.println("Using index: " + esIndex);
        final Promise<Void> promiseMapping = Promise.promise();
        final Promise<Void> promiseScript = Promise.promise();
        createMapping(elasticClientManager, context, esIndex).onComplete(r -> promiseMapping.complete());
        createScript(test.vertx(), elasticClientManager).onComplete(r -> promiseScript.complete());

        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final RedisClient redisClient = new RedisClient(test.vertx(), redisConfig);
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final JsonObject jobConfig = new JsonObject().put("opensearch-options", new JsonObject().put("wait-for", true));
        final MessageReader reader = MessageReader.redis(redisClient, new JsonObject());
        job = IngestJob.createForTest(test.vertx(), elasticClientManager,postgresClient, jobConfig, reader);
        plugin = FakePostgresPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, plugin.getCommunication(), postgresClient, muteService);
    }

    static Future<Void> createMapping(ElasticClientManager elasticClientManager,TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }


    static JsonObject createHtml(String id, String name, String html, String content, final UserInfos user) {
        final JsonObject json = new JsonObject()
                .put("id", id)
                .put("html", html)
                .put("content", content)
                .put("name", name)
                .put("version", 1).put("creator_id", user.getUserId());
        return json;
    }

    //TODO tester audience
    //TODO test pdf
    @Test
    public void shouldSearchResourceWithHtml(TestContext context) {
        final UserInfos user = test.directory().generateUser("userhtml");
        final JsonObject f1 = createHtml("html1", "name1", "<div><h1>MONHTML1</h1></div>", "content1", user);
        final JsonObject f2 = createHtml("html2", "name2", "<div><h1>MONHTML2</h1></div>", "content2", user);
        final Async async = context.async(3);
        plugin.notifyUpsert(user, Arrays.asList(f1, f2)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                    context.assertEquals(2, fetch1.size());
                    resourceService.fetch(user, application, new ResourceSearchOperation().setSearch("MONHTML1")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("html1", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new ResourceSearchOperation().setSearch("name2")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("html2", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new ResourceSearchOperation().setSearch("content2")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("html2", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                }));
            }));
        }));
    }

    @Test
    public void shouldSearchResourceWithSubresources(TestContext context) {
        final UserInfos user = test.directory().generateUser("usernested");
        final ExplorerMessage f1 = ExplorerMessage.upsert(new IdAndVersion("id1", 1L), user, false, plugin.getApplication(), plugin.getResourceType(), plugin.getResourceType()).withCreator(user);
        f1.withSubResourceContent("id1_1", "content1_1", ExplorerMessage.ExplorerContentType.Text, 0L).withSubResourceContent("id1_1", "<h1>html1_1</h1>", ExplorerMessage.ExplorerContentType.Html, 0L);
        f1.withSubResourceContent("id1_2", "content1_2", ExplorerMessage.ExplorerContentType.Text, 0L).withSubResourceContent("id1_2", "<h1>html1_2</h1>", ExplorerMessage.ExplorerContentType.Html, 0L);
        final ExplorerMessage f2 = ExplorerMessage.upsert(new IdAndVersion("id2", 1L), user, false, plugin.getApplication(), plugin.getResourceType(), plugin.getResourceType()).withCreator(user);
        f2.withSubResourceContent("id2_1", "content2_1", ExplorerMessage.ExplorerContentType.Text, 0L).withSubResourceContent("id2_1", "<h1>html2_1</h1>", ExplorerMessage.ExplorerContentType.Html, 0L);
        f2.withSubResourceContent("id2_2", "content2_2", ExplorerMessage.ExplorerContentType.Text, 0L).withSubResourceContent("id2_2", "<h1>html2_2</h1>", ExplorerMessage.ExplorerContentType.Html, 0L);
        final Async async = context.async(3);
        plugin.notifyUpsert(Arrays.asList(f1, f2)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                    System.out.println(fetch1);
                    context.assertEquals(2, fetch1.size());
                    resourceService.fetch(user, application, new ResourceSearchOperation().setSearch("html1_1")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("id1", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new ResourceSearchOperation().setSearch("content2_1")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("id2", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new ResourceSearchOperation().setSearch("content2_2")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("id2", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                }));
            }));
        }));

    }

    @Test
    public void shouldSearchResourceWithComplexCriteria(TestContext context) {
        //TODO
    }


}
