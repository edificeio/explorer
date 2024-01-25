package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.*;
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
import io.vertx.core.json.JsonArray;
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
import org.entcore.common.share.ShareRoles;
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
import java.util.*;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.in;

@RunWith(VertxUnitRunner.class)
public class IngestJobRedisDuplicateTest {

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
    static PostgresClient postgresClient;
    static FailingIngester ingester;

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
        postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final JsonObject jobConfig = new JsonObject().put("opensearch-options", new JsonObject().put("wait-for", true)).put("messageTransformers", new JsonArray().add(new JsonObject().put("id", "htmlAnalyse").put("minLength", 0)));
        final MessageReader reader = MessageReader.redis(test.vertx(), redisClient, new JsonObject());
        final IngestJobMetricsRecorder recorder = IngestJobMetricsRecorderFactory.getIngestJobMetricsRecorder();
        final MessageIngester inner = MessageIngester.elasticWithPgBackup(elasticClientManager, postgresClient, recorder, jobConfig);
        ingester = new FailingIngester(inner);
        job = new IngestJob(test.vertx(), reader, ingester, recorder, jobConfig);
        plugin = FakePostgresPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, plugin.getCommunication(), postgresClient, muteService);
    }

    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }


    static JsonObject create(String id, String name, String content, final UserInfos user) {
        final JsonObject json = new JsonObject()
                .put("id", id)
                .put("rights", new JsonArray().add(ShareRoles.Read.getSerializedForUser("ID")).add(ShareRoles.Read.getSerializedForGroup("ID")))
                .put("content", content)
                .put(ExplorerMessage.CONTENT_HTML_KEY, content)
                .put("name", name)
                .put("version", 1).put("creator_id", user.getUserId());
        return json;
    }

    /**
     * <h1><u>Goal : </u> Avoid duplicate rights on replay</h1>
     * <p>ISSUE: Some array fields (like "rights") are bigger and bigger on each replay (duplication). Theses fields should not growth on replay</p>
     * <p>This error occurs when we have a resources attached to multiple folders. In this case, upsert return multiple rows for one resource, and before ingesting it we merge resources by ids and rights could contains duplicate values</p>
     * <b>Steps</b>
     * <ul>
     *     <li>Create resource with user=redis-fail and id=id1</li>
     *     <li>Create 2 folders from 2 different users: (id=1,creator=redis-fail) (id=2, creator=redis-fail2)</li>
     *     <li>TUpdate resource id=id1</li>
     *     <li>Wait for ingest job to complete</li>
     *     <li>Fetch resource from opensearch</li>
     *     <li>Get result from ingest job</li>
     *     <li>Should fetch 1 resource from OpenSearch</li>
     *     <li>Should have 3 rights in this resource(2 rights + 1 creator right autogenerated)</li>
     *     <li>Should have only 2 unique rights in IngestJobResult object (object used to bulk update opensearch)</li>
     * </ul>
     *
     * @param context
     */
    @Test
    public void shouldIngestResourceWithUniqueRights(TestContext context) {
        final UserInfos user = test.directory().generateUser("redis-fail");
        final UserInfos user2 = test.directory().generateUser("redis-fail2");
        final JsonObject f1 = create("id1", "name1", "content1", user);
        final Async asyn = context.async();
        // create resource
        plugin.notifyUpsert(user, Arrays.asList(f1)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r1 -> {
                job.waitPending().onComplete(context.asyncAssertSuccess(r2 -> {
                    // create multiple folders from different users and join them to the resource
                    final ExplorerMessage m1 = ExplorerMessage.upsert(new IdAndVersion("1", 1), user, false, ExplorerConfig.FOLDER_APPLICATION, ExplorerConfig.FOLDER_TYPE, ExplorerConfig.FOLDER_TYPE).withName("folder1").withCreator(user).withChildrenEntId(new HashSet<>(Arrays.asList("id1")));
                    final ExplorerMessage m2 = ExplorerMessage.upsert(new IdAndVersion("2", 1), user2, false, ExplorerConfig.FOLDER_APPLICATION, ExplorerConfig.FOLDER_TYPE, ExplorerConfig.FOLDER_TYPE).withName("folder1").withCreator(user2).withChildrenEntId(new HashSet<>(Arrays.asList("id1")));
                    new FolderExplorerDbSql(postgresClient).upsert(Arrays.asList(m1, m2)).onComplete(context.asyncAssertSuccess(r3 -> {
                        //update resource after when the relationship is created => it duplicates rights
                        plugin.notifyUpsert(user, Arrays.asList(f1)).onComplete(context.asyncAssertSuccess(r4 -> {
                            // execute job and wait pending task
                            job.execute(true).onComplete(context.asyncAssertSuccess(r5 -> {
                                job.waitPending().onComplete(context.asyncAssertSuccess(r6 -> {
                                    // fetch the resource and check if rights have been duplicated
                                    resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                                        System.out.println(fetch1.encode());
                                        context.assertEquals(1, fetch1.size());
                                        // 2 rights + creator
                                        context.assertEquals(3, fetch1.getJsonObject(0).getJsonArray("rights").size());
                                        // THIS ASSERT WAS FALE BEFORE FIX
                                        context.assertEquals(2, ingester.result.get().getSucceed().get(0).getRights().size());
                                        asyn.complete();
                                    }));
                                }));
                            }));
                        }));
                    }));
                }));
            }));
        }));
    }

    static class FailingIngester implements MessageIngester {
        final MessageIngester inner;
        Optional<IngestJob.IngestJobResult> result = Optional.empty();

        FailingIngester(final MessageIngester inner) {
            this.inner = inner;
        }

        @Override
        public Future<IngestJob.IngestJobResult> ingest(List<ExplorerMessageForIngest> messages) {
            return inner.ingest(messages).map(res -> {
                this.result = Optional.ofNullable(res);
                // reverse fail and success
                return res;
            });
        }
    }

}
