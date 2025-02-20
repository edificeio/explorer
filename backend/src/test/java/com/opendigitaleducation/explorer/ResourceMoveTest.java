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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerPluginMetricsFactory;
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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static io.vertx.core.CompositeFuture.all;
import static java.lang.System.currentTimeMillis;

@RunWith(VertxUnitRunner.class)
@FixMethodOrder(MethodSorters.DEFAULT)
public class ResourceMoveTest {
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
    static final String APPLICATION = ExplorerConfig.FOLDER_APPLICATION;
    private String id3;
//TODO error in move queries?
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
        folderService = new FolderServiceElastic(elasticClientManager, folderPlugin, resourceService);
        helper = folderPlugin.getDbHelper();
        final Async async = context.async();
        final Promise<Void> promiseMapping = Promise.promise();
        final Promise<Void> promiseMappingResource = Promise.promise();
        final Promise<Void> promiseScript = Promise.promise();
        all(promiseMapping.future(), promiseScript.future(),promiseMappingResource.future()).onComplete(e -> async.complete());
        createMapping(elasticClientManager, context, indexFolder).onComplete(r -> promiseMapping.complete());
        createMappingResource(elasticClientManager, context, indexResource).onComplete(r -> promiseMappingResource.complete());
        createScript(test.vertx(), elasticClientManager).onComplete(r -> promiseScript.complete());
        final JsonObject jobConfig = new JsonObject().put("opensearch-options", new JsonObject().put("wait-for", true));
        final MessageReader reader = MessageReader.redis(test.vertx(), redisClient, new JsonObject());
        job = IngestJob.createForTest(test.vertx(), elasticClientManager, postgresClient, jobConfig, reader);
        ExplorerConfig.getInstance().setSkipIndexOfTrashedFolders(true);
        plugin = FakePostgresPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, plugin.getCommunication(), postgresClient, muteService);
    }

    static JsonObject folder(final String name) {
        return folder(name, APPLICATION, ExplorerConfig.FOLDER_TYPE, null);
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

    static JsonObject folder(final String name, final String application, final String resourceType, final Integer parentId) {
        final JsonObject folder = new JsonObject()
                .put("name", name)
                .put("application", application)
                .put("resourceType", resourceType)
                .put("version", 1);
        if (parentId != null) {
            folder.put("parentId", parentId);
        }
        return folder;
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
     * <u>GOAL</u> : Ensure that an application is able to create a resource into a folder using an upsert message
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a folder "folder1" that will contains the resource</li>
     *     <li>Create a resource "resource1" that will be moved into folder1 using a "notifyUpsert" from the plugin</li>
     *     <li>Call the ingest job to ensure all messages has been processed</li>
     *     <li>Fetch all resources which are inside "folder1" for the current user</li>
     *     <li>Ensure that "resource1" is present and the unique resource in "folder1" </li>
     * </ol>
     * @param context Test context
     */
    @Test
    public void shouldCreateAndMoveResource(TestContext context) {
        final Async async = context.async();
        final UserInfos user = test.directory().generateUser("usermoveresource");
        final JsonObject folder1 = folder("folder1_" + currentTimeMillis());
        final JsonObject resource1 = createHtml("html-create-move", "name1", "<div><h1>MONHTML1</h1></div>", "content1", user);
        folderService.create(user, APPLICATION, Arrays.asList(folder1)).compose(r -> {
            id3 = r.get(0).getValue("id").toString();
            return job.execute(true).compose(ra1 -> {
                return folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetchRes -> {
                    context.assertEquals(1, fetchRes.size());
                }));
            });
        }).compose(e -> {
            final Future<Void> future1 = plugin.notifyUpsert(user, Arrays.asList(resource1), Optional.of(Integer.valueOf(id3)));
            return future1.compose(ee -> {
                return job.execute(true);
            });
        }).onComplete(context.asyncAssertSuccess(e->{
            resourceService.fetch(user, FakePostgresPlugin.FAKE_APPLICATION, new ResourceSearchOperation().setParentId(id3)).onComplete(context.asyncAssertSuccess(resources->{
                context.assertEquals(1, resources.size());
                context.assertEquals("html-create-move", resources.getJsonObject(0).getString("assetId"));
                async.complete();
            }));
        }));
    }

}
