package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.redis.RedisClient;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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
import java.util.Optional;

@RunWith(VertxUnitRunner.class)
public class FolderServiceTest {
    private static final TestHelper test = TestHelper.helper();
    //TODO api doc
    //TODO on delete folder => delete resources and sub folders
    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.9.0").withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(("redis:5.0.3-alpine")).withReuse(true);

    static ElasticClientManager elasticClientManager;
    static FolderService folderService;
    static IngestJob job;

    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
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
        final Async async = context.async();
        createMapping(context, index).onComplete(r -> async.complete());
        final MessageReader reader = MessageReader.redis(redisClient, new JsonObject());
        job = IngestJob.create(test.vertx(), elasticClientManager,postgresClient, new JsonObject(), reader);
    }

    static JsonObject folder(final String name) {
        return folder(name, "blog", "blog", null);
    }

    static JsonObject folder(final String name, final String application, final String resourceType) {
        return folder(name, application, resourceType, null);
    }

    static JsonObject folder(final String name, final String application, final String resourceType, final Integer parentId) {
        final JsonObject folder = new JsonObject().put("name", name).put("application", application).put("resourceType", resourceType);
        if (parentId != null) {
            folder.put("parentId", parentId);
        }
        return folder;
    }

    static Future<Void> createMapping(TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("mappingFolder.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    @Test
    public void shouldCreateFolderTree(TestContext context) {
        final JsonObject f1 = folder("folder1");
        final JsonObject f2 = folder("folder2");
        final JsonObject f3 = folder("folder3");
        final JsonObject f3_1 = folder("folder3_1");
        final JsonObject f3_1_1 = folder("folder3_1_1");
        final UserInfos user = test.directory().generateUser("usermove");
        folderService.create(user, Arrays.asList(f1, f2, f3)).onComplete(context.asyncAssertSuccess(r -> {
            final String f3_id = r.get(2).getString("_id");
            folderService.create(user, f3_1.put("parentId", f3_id)).onComplete(context.asyncAssertSuccess(r2 -> {
                folderService.create(user, f3_1_1.put("parentId", r2)).onComplete(context.asyncAssertSuccess(r3 -> {
                    job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                        folderService.fetch(user, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                            context.assertEquals(3, fetch1.size());
                            final JsonArray a3 = fetch1.getJsonObject(2).getJsonArray("ancestors");
                            final JsonArray ch3 = fetch1.getJsonObject(2).getJsonArray("childrenIds");
                            context.assertEquals(1, a3.size());
                            context.assertEquals(1, ch3.size());
                            context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3.getString(0));
                            folderService.fetch(user, Optional.of(f3_id)).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                context.assertEquals(1, fetch2.size());
                                final String id3_1 = fetch2.getJsonObject(0).getString("_id");
                                final JsonArray a3_1 = fetch2.getJsonObject(0).getJsonArray("ancestors");
                                final JsonArray ch3_1 = fetch2.getJsonObject(0).getJsonArray("childrenIds");
                                context.assertEquals(r2, id3_1);
                                context.assertEquals(2, a3_1.size());
                                context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3_1.getString(0));
                                context.assertEquals(f3_id, a3_1.getString(1));
                                context.assertEquals(1, ch3_1.size());
                                folderService.fetch(user, Optional.of(id3_1)).onComplete(context.asyncAssertSuccess(fetch3 -> {
                                    context.assertEquals(1, fetch3.size());
                                    final String id3_1_1 = fetch3.getJsonObject(0).getString("_id");
                                    final JsonArray a3_1_1 = fetch3.getJsonObject(0).getJsonArray("ancestors");
                                    final JsonArray ch3_1_1 = fetch3.getJsonObject(0).getJsonArray("childrenIds");
                                    context.assertEquals(r3, id3_1_1);
                                    context.assertEquals(3, a3_1_1.size());
                                    context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3_1_1.getString(0));
                                    context.assertEquals(f3_id, a3_1_1.getString(1));
                                    context.assertEquals(id3_1, a3_1_1.getString(2));
                                    context.assertEquals(0, ch3_1_1.size());
                                }));
                            }));
                        }));
                    }));
                }));
            }));
        }));
    }

    @Test
    public void shouldMoveSubTree(TestContext context) {
        final JsonObject f1 = folder("move1");
        final JsonObject f2 = folder("move2");
        final JsonObject f2_1 = folder("folder2_1");
        final JsonObject f2_1_1 = folder("folder2_1_1");
        final UserInfos user = test.http().sessionUser();
        folderService.create(user, Arrays.asList(f1, f2)).onComplete(context.asyncAssertSuccess(r -> {
            final String f2_id = r.get(1).getString("_id");
            final Optional<String> source = Optional.of(f2_id);
            final Optional<String> dest = Optional.of(r.get(0).getString("_id"));
            folderService.create(user, f2_1.put("parentId", f2_id)).onComplete(context.asyncAssertSuccess(r2 -> {
                f2.put("_id", r2);
                folderService.create(user, f2_1_1.put("parentId", r2)).onComplete(context.asyncAssertSuccess(r3 -> {
                    job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                        folderService.fetch(user, source).onComplete(context.asyncAssertSuccess(fetch0 -> {
                            context.assertEquals(1, fetch0.size());
                            folderService.fetch(user, dest).onComplete(context.asyncAssertSuccess(fetch -> {
                                context.assertEquals(0, fetch.size());
                                final String f2Id = f2.getString("_id");
                                folderService.move(user, f2Id, dest).onComplete(context.asyncAssertSuccess(move -> {
                                    job.execute(true).onComplete(context.asyncAssertSuccess(r5 -> {
                                        folderService.fetch(user, dest).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                            folderService.fetch(user, source).onComplete(context.asyncAssertSuccess(fetch3 -> {
                                                context.assertEquals(1, fetch2.size());
                                                context.assertEquals(0, fetch3.size());
                                                context.assertEquals(dest.get(), fetch2.getJsonObject(0).getJsonArray("ancestors").getString(1));
                                                System.out.println("end shouldMoveSubTree");
                                            }));
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

}
