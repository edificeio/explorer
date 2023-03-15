package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.FolderSearchOperation;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
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
import java.util.*;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static io.vertx.core.CompositeFuture.all;
import static java.lang.System.currentTimeMillis;

@RunWith(VertxUnitRunner.class)
@FixMethodOrder(MethodSorters.DEFAULT)
public class FolderTrashTest {
    private static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static ElasticsearchContainer esContainer = test.database().createOpenSearchContainer().withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine")).withExposedPorts(6379);

    static ElasticClientManager elasticClientManager;
    static FolderService folderService;
    static IngestJob job;
    static FolderExplorerDbSql helper;
    static final String APPLICATION = ExplorerConfig.FOLDER_APPLICATION;
    private String id3;
    private String id3_1;
    private String id3_1_1;

    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        IngestJobMetricsRecorderFactory.init(test.vertx(), new JsonObject());
        ExplorerPluginMetricsFactory.init(test.vertx(), new JsonObject());
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final String index = ExplorerConfig.DEFAULT_FOLDER_INDEX + "_" + System.currentTimeMillis();
        System.out.println("Using index: " + index);
        ExplorerConfig.getInstance().setEsIndex("explorer", index);
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final RedisClient redisClient = new RedisClient(test.vertx(), redisConfig);
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final FolderExplorerPlugin folderPlugin = FolderExplorerPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        folderService = new FolderServiceElastic(elasticClientManager, folderPlugin);
        helper = folderPlugin.getDbHelper();
        final Async async = context.async();
        final Promise<Void> promiseMapping = Promise.promise();
        final Promise<Void> promiseScript = Promise.promise();
        all(Arrays.asList(promiseMapping.future(), promiseScript.future()))
                .onComplete(e -> async.complete());
        createMapping(elasticClientManager, context, index).onComplete(r -> promiseMapping.complete());
        createScript(test.vertx(), elasticClientManager).onComplete(r -> promiseScript.complete());
        final MessageReader reader = MessageReader.redis(redisClient, new JsonObject());
        job = IngestJob.create(test.vertx(), elasticClientManager, postgresClient, new JsonObject()
                .put("opensearch-options", new JsonObject().put("wait-for", true)), reader);
        ExplorerConfig.getInstance().setSkipIndexOfTrashedFolders(true);
    }

    static JsonObject folder(final String name) {
        return folder(name, APPLICATION, ExplorerConfig.FOLDER_TYPE, null);
    }

    static JsonObject folder(final String name, final String application, final String resourceType) {
        return folder(name, application, resourceType, null);
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

    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingFolder.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    @Test
    public void shouldCreateFolderTree(TestContext context) {
        final Async async = context.async();
        final JsonObject f1 = folder("folder1_" + currentTimeMillis());
        final JsonObject f2 = folder("folder2_" + currentTimeMillis());
        final JsonObject f3 = folder("folder3_" + currentTimeMillis());
        final JsonObject f3_1 = folder("folder3_1_" + currentTimeMillis());
        final JsonObject f3_1_1 = folder("folder3_1_1_" + currentTimeMillis());
        final UserInfos user = test.directory().generateUser("usermove");
        folderService.create(user, APPLICATION, Arrays.asList(f1, f2, f3)).onComplete(context.asyncAssertSuccess(r -> {
            id3 = r.get(2).getValue("id").toString();
            job.execute(true).onComplete(context.asyncAssertSuccess(ra1 -> {
                folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(tutu -> {
                    context.assertEquals(3, tutu.result().size());
                });
                folderService.create(user, APPLICATION, f3_1.put("parentId", id3)).onComplete(context.asyncAssertSuccess(r2 -> {
                    job.execute(true).onComplete(context.asyncAssertSuccess(ra2 -> {
                        folderService.create(user, APPLICATION, f3_1_1.put("parentId", r2)).onComplete(context.asyncAssertSuccess(r3 -> {
                            job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                                folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                                    context.assertEquals(3, fetch1.size());
                                    final JsonArray a3 = fetch1.getJsonObject(2).getJsonArray("ancestors");
                                    final JsonArray ch3 = fetch1.getJsonObject(2).getJsonArray("childrenIds");
                                    context.assertEquals(1, a3.size());
                                    context.assertEquals(1, ch3.size());
                                    context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3.getString(0));
                                    folderService.fetch(user, APPLICATION, Optional.of(id3)).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                        context.assertEquals(1, fetch2.size());
                                        id3_1 = fetch2.getJsonObject(0).getValue("_id").toString();
                                        final JsonArray a3_1 = fetch2.getJsonObject(0).getJsonArray("ancestors");
                                        final JsonArray ch3_1 = fetch2.getJsonObject(0).getJsonArray("childrenIds");
                                        context.assertEquals(r2, id3_1);
                                        context.assertEquals(2, a3_1.size());
                                        context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3_1.getString(0));
                                        context.assertEquals(id3, a3_1.getString(1));
                                        context.assertEquals(1, ch3_1.size());
                                        folderService.fetch(user, APPLICATION, Optional.of(id3_1)).onComplete(context.asyncAssertSuccess(fetch3 -> {
                                            context.assertEquals(1, fetch3.size());
                                            id3_1_1 = fetch3.getJsonObject(0).getValue("_id").toString();
                                            final JsonArray a3_1_1 = fetch3.getJsonObject(0).getJsonArray("ancestors");
                                            final JsonArray ch3_1_1 = fetch3.getJsonObject(0).getJsonArray("childrenIds");
                                            context.assertEquals(r3, id3_1_1);
                                            context.assertEquals(3, a3_1_1.size());
                                            context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3_1_1.getString(0));
                                            context.assertEquals(id3, a3_1_1.getString(1));
                                            context.assertEquals(id3_1, a3_1_1.getString(2));
                                            context.assertEquals(0, ch3_1_1.size());
                                            onTrash(context, user).compose(e -> {
                                                return onCleanFolders(context);
                                            }).onComplete(e -> {
                                                async.complete();
                                            });
                                        }));
                                    }));
                                }));
                            }));
                        }));
                    }));
                }));
            }));
        }));
    }

    protected Future onTrash(TestContext context, final UserInfos user) {
        final UserInfos user2 = test.directory().generateUser("user_trash2");
        final Async async = context.async(6);

        CompositeFuture.all(folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch2 -> {
            context.assertEquals(3, fetch2.size());
            async.countDown();
        })),
        folderService.fetch(user, APPLICATION, Optional.of(id3)).onComplete(context.asyncAssertSuccess(fetch2 -> {
            context.assertEquals(1, fetch2.size());
            async.countDown();
        }))).onComplete(context.asyncAssertSuccess(e -> {
            folderService.trash(user, new HashSet<>(Arrays.asList(id3)), APPLICATION, true).onComplete(context.asyncAssertSuccess(r -> {
                job.execute(true).onComplete(context.asyncAssertSuccess(r4a -> {
                    folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch -> {
                        context.assertEquals(2, fetch.size());
                        final String fId = fetch.getJsonObject(0).getValue("_id").toString();
                        final Set<String> ids = new HashSet<>();
                        ids.add(fId);
                        folderService.trash(user2, ids, APPLICATION, true).onComplete(context.asyncAssertFailure(move -> {
                            context.assertEquals(move.getMessage(), "folder.trash.id.invalid");
                            async.countDown();
                        }));
                        folderService.trash(user, ids, APPLICATION, true).onComplete(context.asyncAssertSuccess(move -> {
                            context.assertEquals(move.get(0).getValue("id").toString(), fId);
                            async.countDown();
                            folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                context.assertEquals(2, fetch2.size());
                                async.countDown();
                            }));
                            folderService.fetch(user, APPLICATION, Optional.of(fId)).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                context.assertEquals(0, fetch2.size());
                                async.countDown();
                            }));
                        }));
                    }));
                }));
            }));
        }));
        final Promise promise = Promise.promise();
        async.handler(promise.future());
        return promise.future();
    }

    protected Future onCleanFolders(TestContext context) {
        final Async async = context.async();
        helper.selectFolderIds().onComplete(context.asyncAssertSuccess(allIds ->{
            context.assertEquals(5, allIds.size());
            helper.deleteTrashedFolderIds().onComplete(context.asyncAssertSuccess(deleted -> {
                context.assertEquals(4, deleted.size());
                context.assertTrue(deleted.contains(id3));
                context.assertTrue(deleted.contains(id3_1));
                context.assertTrue(deleted.contains(id3_1_1));
                helper.selectFolderIds().onComplete(context.asyncAssertSuccess(allIdsAfter ->{
                    context.assertEquals(1, allIdsAfter.size());
                    async.complete();
                }));
            }));
        }));
        final Promise promise = Promise.promise();
        async.handler(promise.future());
        return promise.future();
    }

}
