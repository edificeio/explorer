package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.SearchOperation;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerMessage;
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

import java.net.URI;
import java.util.Arrays;

@RunWith(VertxUnitRunner.class)
public class ResourceServiceTest {
    private static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.9.3").withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(("redis:5.0.3-alpine")).withReuse(true);
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
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final Async async = context.async();
        esIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
        ExplorerConfig.getInstance().setEsIndex(FakePostgresPlugin.FAKE_APPLICATION, esIndex);
        application = FakePostgresPlugin.FAKE_APPLICATION;
        System.out.println("Using index: " + esIndex);
        createMapping(elasticClientManager, context, esIndex).onComplete(r -> async.complete());
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final RedisClient redisClient = new RedisClient(test.vertx(), redisConfig);
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final MessageReader reader = MessageReader.redis(redisClient, new JsonObject());
        job = IngestJob.create(test.vertx(), elasticClientManager,postgresClient, new JsonObject(), reader);
        plugin = FakePostgresPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, plugin.getCommunication(), postgresClient);
    }

    static Future<Void> createMapping(ElasticClientManager elasticClientManager,TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }


    static JsonObject createHtml(String id, String name, String html, String content) {
        final JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("html", html);
        json.put("content", content);
        json.put("name", name);
        return json;
    }

    //TODO api doc
    //TODO on delete folder => delete resources and sub folders
    //TODO persist predictible ID in bus to skip one step (optim)
    //TODO test failed case (ingest failed, ingest too many error, ingest too big payload, message read failed, message update status failed...)
    //TODO test metrics
    //TODO add more logs
    //TODO before delete resource => delete in postgres (cascade resource_folders)
    //TODO tester audience
    //TODO tester reindexation gros volume (workspace ou conversation) + test charge
    //TODO tester api en lot / recursive
    //TODO test recherche multi critere complexe
    //TODO test pdf
    //TODO chargement automatique du mapping au démarrage
    //TODO config pf recette (redis stream, config du module...)
    //TODO adaptative maxbatch size (according to max payload size, previous error?...)
    //TODO better redis stream ID? "application-date"
    //https://docs.google.com/presentation/d/1xtPS--PhtBSGmTAYl74BIqDIbZE0v99p253W9GKFQJA/edit#slide=id.gc52769f62b_0_63
    @Test
    public void shouldSearchResourceWithHtml(TestContext context) {
        final UserInfos user = test.directory().generateUser("userhtml");
        final JsonObject f1 = createHtml("html1", "name1", "<div><h1>MONHTML1</h1></div>", "content1");
        final JsonObject f2 = createHtml("html2", "name2", "<div><h1>MONHTML2</h1></div>", "content2");
        final Async async = context.async(3);
        plugin.notifyUpsert(user, Arrays.asList(f1, f2)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                resourceService.fetch(user, application, new SearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                    context.assertEquals(2, fetch1.size());
                    resourceService.fetch(user, application, new SearchOperation().setSearch("MONHTML1")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("html1", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new SearchOperation().setSearch("name2")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("html2", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new SearchOperation().setSearch("content2")).onComplete(context.asyncAssertSuccess(fetch2 -> {
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
        final ExplorerMessage f1 = ExplorerMessage.upsert("id1", user, false);
        f1.withSubResourceContent("id1_1", "content1_1", ExplorerMessage.ExplorerContentType.Text).withSubResourceContent("id1_1", "<h1>html1_1</h1>", ExplorerMessage.ExplorerContentType.Html);
        f1.withSubResourceContent("id1_2", "content1_2", ExplorerMessage.ExplorerContentType.Text).withSubResourceContent("id1_2", "<h1>html1_2</h1>", ExplorerMessage.ExplorerContentType.Html);
        final ExplorerMessage f2 = ExplorerMessage.upsert("id2", user, false);
        f2.withSubResourceContent("id2_1", "content2_1", ExplorerMessage.ExplorerContentType.Text).withSubResourceContent("id2_1", "<h1>html2_1</h1>", ExplorerMessage.ExplorerContentType.Html);
        f2.withSubResourceContent("id2_2", "content2_2", ExplorerMessage.ExplorerContentType.Text).withSubResourceContent("id2_2", "<h1>html2_2</h1>", ExplorerMessage.ExplorerContentType.Html);
        final Async async = context.async(3);
        plugin.notifyUpsert(Arrays.asList(f1, f2)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                resourceService.fetch(user, application, new SearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                    System.out.println(fetch1);
                    context.assertEquals(2, fetch1.size());
                    resourceService.fetch(user, application, new SearchOperation().setSearch("html1_1")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("id1", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new SearchOperation().setSearch("content2_1")).onComplete(context.asyncAssertSuccess(fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        context.assertEquals("id2", fetch2.getJsonObject(0).getString("assetId"));
                        async.countDown();
                    }));
                    resourceService.fetch(user, application, new SearchOperation().setSearch("content2_2")).onComplete(context.asyncAssertSuccess(fetch2 -> {
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
