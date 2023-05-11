package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.DefaultMuteService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
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
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static io.vertx.core.CompositeFuture.all;
import static java.lang.System.currentTimeMillis;

@RunWith(VertxUnitRunner.class)
@FixMethodOrder(MethodSorters.DEFAULT)
public class ResourceTrashTest {
    private static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static ElasticsearchContainer esContainer = test.database().createOpenSearchContainer().withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine")).withExposedPorts(6379);
    static FakePostgresPlugin plugin;
    static ElasticClientManager elasticClientManager;
    static FolderService folderService;
    static IngestJob job;
    static FolderExplorerDbSql helper;
    static ResourceService resourceService;

    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        IngestJobMetricsRecorderFactory.init(test.vertx(), new JsonObject());
        ExplorerPluginMetricsFactory.init(test.vertx(), new JsonObject());
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final String indexFolder = ExplorerConfig.DEFAULT_FOLDER_INDEX + "_" + System.currentTimeMillis();
        final String indexResource = FakePostgresPlugin.FAKE_APPLICATION + "_" + System.currentTimeMillis();
        System.out.println("Using index: " + indexFolder+"|"+indexResource);
        ExplorerConfig.getInstance().setEsIndex("explorer", indexFolder);
        ExplorerConfig.getInstance().setEsIndex(FakePostgresPlugin.FAKE_APPLICATION, indexResource);
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final RedisClient redisClient = new RedisClient(test.vertx(), redisConfig);
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final FolderExplorerPlugin folderPlugin = FolderExplorerPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        folderService = new FolderServiceElastic(elasticClientManager, folderPlugin);
        helper = folderPlugin.getDbHelper();
        final Async async = context.async();
        final Promise<Void> promiseMapping = Promise.promise();
        final Promise<Void> promiseMappingResource = Promise.promise();
        final Promise<Void> promiseScript = Promise.promise();
        all(Arrays.asList(promiseMapping.future(), promiseScript.future(),promiseMappingResource.future()))
                .onComplete(e -> async.complete());
        createMapping(elasticClientManager, context, indexFolder).onComplete(r -> promiseMapping.complete());
        createMappingResource(elasticClientManager, context, indexResource).onComplete(r -> promiseMappingResource.complete());
        createScript(test.vertx(), elasticClientManager).onComplete(r -> promiseScript.complete());
        final JsonObject jobConfig = new JsonObject().put("opensearch-options", new JsonObject().put("wait-for", true));
        final MessageReader reader = MessageReader.redis(redisClient, new JsonObject());
        job = IngestJob.createForTest(test.vertx(), elasticClientManager, postgresClient, jobConfig, reader);
        ExplorerConfig.getInstance().setSkipIndexOfTrashedFolders(true);
        plugin = FakePostgresPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, plugin.getCommunication(), postgresClient, muteService);
    }

    static ExplorerMessage createHtml(String id, String name, String html, final UserInfos user) {
        return ExplorerMessage.upsert(new IdAndVersion(id, 1), user, false, plugin.getApplication(), plugin.getResourceType(), plugin.getResourceType()).withName(name).withContent(html, ExplorerMessage.ExplorerContentType.Html).withCreator(user).withCreatedAt(new Date());
    }

    static Future<Void> createMappingResource(ElasticClientManager elasticClientManager,TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }
    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingFolder.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    /**
     * <u>GOAL</u> : Ensure that an application is able to create a resource and trash it by user and also for all users
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource "resource1" that will be trashed for all users using a "notifyUpsert" from the plugin</li>
     *     <li>Create a resource "resource2" that will be trashed for one user using a "notifyUpsert" from the plugin</li>
     *     <li>Call the ingest job to ensure all messages has been processed</li>
     *     <li>Fetch all non-trashed resources for the current user: it should be empty</li>
     *     <li>Fetch all trashed resources: should return resource1 and resource2</li>
     *     <li>Ensure that "resource1" has trashed=true AND trashedBy empty </li>
     *     <li>Ensure that "resource2" has trashed=false AND trashedBy non empty </li>
     * </ol>
     *
     * @param context Test context
     */
    @Test
    public void shouldCreateAndTrashResource(TestContext context) {
        final Async async = context.async();
        final UserInfos user = test.directory().generateUser("user-trash-resource");
        final ExplorerMessage resource1 = createHtml("resource-trash1", "name1", "<div><h1>MONHTML1</h1></div>", user).withTrashed(true);
        final ExplorerMessage resource2 = createHtml("resource-trash2", "name2", "<div><h1>MONHTML2</h1></div>", user).withTrashedBy(Arrays.asList(user.getUserId()));
        final Future<Void> future1 = plugin.notifyUpsert(Arrays.asList(resource1, resource2));
        future1.compose(upsertRes -> {
            return job.execute(true);
        }).onComplete(context.asyncAssertSuccess(executeRes -> {
            resourceService.fetch(user, plugin.getApplication(), new ResourceSearchOperation().setSearchEverywhere(true)).onComplete(context.asyncAssertSuccess(resources -> {
                context.assertEquals(2, resources.size());
            })).compose(zeroFetch -> {
                return resourceService.fetch(user, plugin.getApplication(), new ResourceSearchOperation().setSearchEverywhere(true).setTrashed(false)).onComplete(context.asyncAssertSuccess(resources -> {
                    System.out.println(resources);
                    context.assertEquals(0, resources.size());
                }));
            }).compose(firstFetch -> {
                return resourceService.fetch(user, plugin.getApplication(), new ResourceSearchOperation().setSearchEverywhere(true).setTrashed(true)).onComplete(context.asyncAssertSuccess(resources -> {
                    context.assertEquals(2, resources.size());
                    context.assertEquals("resource-trash1", resources.getJsonObject(0).getString("assetId"));
                    context.assertEquals("resource-trash2", resources.getJsonObject(1).getString("assetId"));
                    context.assertEquals(true, resources.getJsonObject(0).getBoolean("trashed"));
                    context.assertEquals(0, resources.getJsonObject(0).getJsonArray("trashedBy", new JsonArray()).size());
                    context.assertEquals(false, resources.getJsonObject(1).getBoolean("trashed"));
                    context.assertEquals(1, resources.getJsonObject(1).getJsonArray("trashedBy").size());
                    async.complete();
                }));
            });
        }));
    }

}
