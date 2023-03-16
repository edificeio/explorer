package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.controllers.ExplorerController;
import com.opendigitaleducation.explorer.filters.UpdateFilter;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.HttpMethod;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Request;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.explorer.ExplorerPluginMetricsFactory;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.user.UserInfos;
import org.entcore.test.HttpTestHelper;
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
import java.util.ArrayList;
import java.util.UUID;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.executeJobNTimes;

@RunWith(VertxUnitRunner.class)
public class ExplorerControllerTest {
    protected static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static ElasticsearchContainer esContainer = test.database().createOpenSearchContainer().withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine")).withExposedPorts(6379);
    static ExplorerController controller;
    static IngestJob job;
    static FakePostgresPlugin fakePlugin;
    static ElasticClientManager esClientManager;
    static UpdateFilter folderFilter = new UpdateFilter();
    static final String application = FakePostgresPlugin.FAKE_APPLICATION;

    @BeforeClass
    public static void setUp(final TestContext context) throws Exception {
        EventStoreFactory.getFactory().setVertx(test.vertx());
        ExplorerPluginMetricsFactory.init(test.vertx(), new JsonObject());
        IngestJobMetricsRecorderFactory.init(test.vertx(), new JsonObject());
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final RedisClient redisClient = new RedisClient(test.vertx(), redisConfig);
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        final String folderIndex = ExplorerConfig.DEFAULT_FOLDER_INDEX + "_" + System.currentTimeMillis();
        final String resourceIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
        ExplorerConfig.getInstance().setEsIndex(ExplorerConfig.FOLDER_APPLICATION, folderIndex);
        ExplorerConfig.getInstance().setEsIndex(FakePostgresPlugin.FAKE_APPLICATION, resourceIndex);
        System.out.println("Using index: " + folderIndex + " / " + resourceIndex);
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        esClientManager = new ElasticClientManager(test.vertx(), uris);
        final FolderExplorerPlugin folderPlugin = FolderExplorerPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        final IExplorerPluginCommunication communication = folderPlugin.getCommunication();
        final FolderService folderService = new FolderServiceElastic(esClientManager, folderPlugin);
        final ResourceService resourceService = new ResourceServiceElastic(esClientManager, shareTableManager, communication, postgresClient);
        controller = new ExplorerController(folderService, resourceService);
        controller.init(test.vertx(), new JsonObject(), null, null);
        fakePlugin = FakePostgresPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        test.http().mockJsonValidator();
        test.directory().mockUserPreferences(new JsonObject());
        UpdateFilter.setFolderService(folderService);
        FolderServiceTest.createMapping(esClientManager, context, folderIndex).onComplete(context.asyncAssertSuccess());
        IngestJobTest.createMapping(esClientManager, context, resourceIndex).onComplete(context.asyncAssertSuccess());
        createScript(test.vertx(), esClientManager).onComplete(context.asyncAssertSuccess());
        //flush redis
        final Async async = context.async();
        redisClient.getClient().flushall(new ArrayList<>(), e -> {
            final MessageReader reader = MessageReader.redis(redisClient, new JsonObject());
            job = IngestJob.createForTest(test.vertx(), esClientManager, postgresClient, new JsonObject(), reader);
            //start job too create streams
            job.start().compose(ee -> {
                return job.stop();
            }).onComplete(context.asyncAssertSuccess(eee -> {
                async.complete();
            }));
        });
    }

    @Test
    public void shouldCrudFolder(final TestContext context) throws Exception {
        final UserInfos user = test.http().sessionUser();
        //create folder
        final JsonObject folder = FolderServiceTest.folder("folder1").put("application", application);
        final HttpTestHelper.TestHttpServerRequest createReq = test.http().post("/folders", new JsonObject(), folder);
        final Promise<JsonObject> promiseCreate = Promise.promise();
        final JsonObject create = new JsonObject();
        createReq.response().endJsonHandler(e -> {
            context.assertNotNull(e.getString("id"));
            create.mergeIn(e);
            promiseCreate.complete(e);
        });
        controller.createFolder(createReq.withSession(user));
        //trigger job
        promiseCreate.future().compose(e -> {
            final Promise<JsonObject> promiseTrigger = Promise.promise();
            try {
                final JsonObject params = new JsonObject().put("timeout", "180000");
                final HttpTestHelper.TestHttpServerRequest triggerReq = test.http().post("/folders", params, folder);
                triggerReq.response().endJsonHandler(ee -> {
                    promiseTrigger.complete(ee);
                });
                controller.triggerJob(triggerReq.withSession(user));
            } catch (Exception exception) {
                context.fail(exception);
            }
            return promiseTrigger.future();
        })
        .compose(e -> executeJobNTimes(job, 1, context))
        .compose(createE -> {
            //list folders
            final Promise<Void> promiseList = Promise.promise();
            try {
                //list folder
                final HttpTestHelper.TestHttpServerRequest getReq = test.http().get("/folders", new JsonObject());
                final JsonObject listFolders = new JsonObject();
                getReq.response().jsonHandler(e -> {
                    listFolders.mergeIn(e);
                });
                getReq.response().endHandler(e -> {
                    context.assertEquals(1, listFolders.getJsonArray("folders").size());
                    promiseList.complete();
                });
                controller.getFolders(getReq.withSession(user));
            } catch (Exception exception) {
                promiseList.fail(exception);
            }
            return promiseList.future();
        }).compose(list -> {
            //get children
            final Promise<Void> promiseChild = Promise.promise();
            try {
                final String id = create.getString("id");
                final HttpTestHelper.TestHttpServerRequest childReq = test.http().get("/folders", new JsonObject().put("id", id));
                childReq.response().endJsonHandler(e -> {
                    context.assertEquals(0, e.getJsonArray("folders").size());
                    promiseChild.complete();
                });
                controller.getFoldersById(childReq.withSession(user));
            } catch (Exception exception) {
                promiseChild.fail(exception);
            }
            return promiseChild.future();
        }).compose(list -> {
            //update
            final Promise<Void> promiseUpdate = Promise.promise();
            try {
                final String id = create.getString("id");
                final JsonObject update = new JsonObject().put("name", "name2").put("application", application);
                final HttpTestHelper.TestHttpServerRequest updateReq = test.http().put("/folders", new JsonObject().put("id", id), update);
                updateReq.response().endJsonHandler(e -> {
                    context.assertNotNull(e.getString("id"));
                    promiseUpdate.complete();
                });
                controller.updateFolder(updateReq.withSession(user));
            } catch (Exception exception) {
                promiseUpdate.fail(exception);
            }
            return promiseUpdate.future();
        }).compose(e->{
            //trigger job
            final Promise<JsonObject> promiseTrigger = Promise.promise();
            try {
                final JsonObject params = new JsonObject().put("timeout", "180000");
                final HttpTestHelper.TestHttpServerRequest triggerReq = test.http().post("/folders", params, folder);
                triggerReq.response().endJsonHandler(ee -> {
                    promiseTrigger.complete(ee);
                });
                controller.triggerJob(triggerReq.withSession(user));
            } catch (Exception exception) {
                context.fail(exception);
            }
            return promiseTrigger.future();
        }).compose(e -> executeJobNTimes(job, 1, context))
        .compose(update -> {
            //get context
            final Promise<Void> promiseContext = Promise.promise();
            try {
                final HttpTestHelper.TestHttpServerRequest contextReq = test.http().get("/context", new JsonObject().put("application", application));
                contextReq.response().endJsonHandler(e -> {
                    final JsonArray folders = e.getJsonArray("folders");
                    context.assertEquals(1, folders.size());
                    context.assertEquals("name2", folders.getJsonObject(0).getString("name"));
                    promiseContext.complete();
                });
                controller.getContext(contextReq.withSession(user));
            } catch (Exception exception) {
                promiseContext.fail(exception);
            }
            return promiseContext.future();
        }).compose(children -> {
            //delete
            final Promise<Void> promiseDelete = Promise.promise();
            try {
                final String id = create.getString("id");
                final JsonObject payload = new JsonObject().put("folderIds", new JsonArray().add(id)).put("resourceIds", new JsonArray());
                final HttpTestHelper.TestHttpServerRequest delReq = test.http().post("", new JsonObject(), payload);
                delReq.response().endJsonHandler(e -> {
                    context.assertEquals(1, e.getJsonArray("folders").size());
                    promiseDelete.complete();
                });
                controller.deleteBatch(delReq.withSession(user));
            } catch (Exception exception) {
                promiseDelete.fail(exception);
            }
            return promiseDelete.future();
        }).onComplete(context.asyncAssertSuccess());
    }

    @Test
    public void shouldCrudResource(final TestContext context) throws Exception {
        final UserInfos user = test.http().sessionUser();
        user.setUserId(UUID.randomUUID().toString());
        final JsonObject doc1 = IngestJobTest.create(user, "id1", "name1", "text1");
        fakePlugin.setVersion(doc1, 1);
        fakePlugin.notifyUpsert(user, doc1).compose(e -> {
            //get metrics
            final Promise<Void> promiseMetrics = Promise.promise();
            try {
                final HttpTestHelper.TestHttpServerRequest getMetrics = test.http().get("/metrics", new JsonObject());
                getMetrics.response().endJsonHandler(json -> {
                    promiseMetrics.complete();
                });
                controller.getMetrics(getMetrics.withSession(user));
            } catch (Exception ex) {
                promiseMetrics.fail(ex);
            }
            return promiseMetrics.future();
        }).compose(e -> {
            //trigger job
            final Promise<Void> promiseTrigger = Promise.promise();
            try {
                final HttpTestHelper.TestHttpServerRequest getMetrics = test.http().get("/jobs", new JsonObject());
                getMetrics.response().endJsonHandler(json -> {
                    promiseTrigger.complete();
                });
                controller.triggerJob(getMetrics.withSession(user));
            } catch (Exception ex) {
                promiseTrigger.fail(ex);
            }
            return promiseTrigger.future();
        }).compose(e -> executeJobNTimes(job, 1, context))
        .compose(e -> {
            //fetch resources
            final Promise<Void> promiseFetch = Promise.promise();
            try {
                final HttpTestHelper.TestHttpServerRequest fetchReq = test.http().get("/resources", new JsonObject().put("application", application));
                fetchReq.response().endJsonHandler(json -> {
                    context.assertEquals(1, json.getJsonArray("resources").size());
                    promiseFetch.complete();
                });
                controller.getResources(fetchReq.withSession(user));
            } catch (Exception ex) {
                promiseFetch.fail(ex);
            }
            return promiseFetch.future();
        }).compose(e -> {
            //get contexts
            final Promise<Void> promiseFetch = Promise.promise();
            try {
                final HttpTestHelper.TestHttpServerRequest fetchReq = test.http().get("/context", new JsonObject().put("application", application));
                fetchReq.response().endJsonHandler(json -> {
                    context.assertEquals(1, json.getJsonArray("resources").size());
                    promiseFetch.complete();
                });
                controller.getContext(fetchReq.withSession(user));
            } catch (Exception ex) {
                promiseFetch.fail(ex);
            }
            return promiseFetch.future();
        }).onComplete(context.asyncAssertSuccess());
    }

    @Test
    public void shouldMoveResource(final TestContext context) throws Exception {
        final UserInfos user = test.http().sessionUser();
        //TODO
    }

    @Test
    public void shouldAuthorizeFolder(final TestContext context) throws Exception {
        final JsonObject params = new JsonObject().put("timeout", "1800000");
        final UserInfos user = test.http().sessionUser();
        //create folder
        final JsonObject folder = FolderServiceTest.folder("folder1").put("application", application);
        fakePlugin.setVersion(folder, 1);
        final HttpTestHelper.TestHttpServerRequest createReq = test.http().post("/folders", params, folder);
        final Promise<JsonObject> promiseCreate = Promise.promise();
        final JsonObject create = new JsonObject();
        createReq.response().endJsonHandler(e -> {
            context.assertNotNull(e.getString("id"));
            create.mergeIn(e);
            promiseCreate.complete(e);
        });
        controller.createFolder(createReq.withSession(user));
        //list folders
        final Async async = context.async();
        //trigger job
        promiseCreate.future().compose(e -> {
            final Promise<JsonObject> promiseTrigger = Promise.promise();
            try {
                final HttpTestHelper.TestHttpServerRequest triggerReq = test.http().post("/folders", params, folder);
                triggerReq.response().endJsonHandler(ee -> {
                    promiseTrigger.complete(ee);
                });
                controller.triggerJob(triggerReq.withSession(user));
            } catch (Exception exception) {
                context.fail(exception);
            }
            return promiseTrigger.future();
        }).compose(e -> executeJobNTimes(job, 1, context))
        .onComplete(createE -> {
            final String id = create.getString("id");
            final Binding binding = test.http().binding(HttpMethod.POST, ExplorerController.class, "updateFolder");
            final HttpTestHelper.TestHttpServerRequest fetchReq = test.http().put("/folder", new JsonObject().put("id", id));
            folderFilter.authorize(fetchReq, binding, user, e -> {
                context.assertTrue(e);
                async.complete();
            });
        });
    }
}
