package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.FolderSearchOperation;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
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
import org.junit.Test;
import org.junit.runner.RunWith;
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
public class FolderServiceTest {
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
    static final String APPLICATION = "test_service";

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
    }

    static JsonObject folder(final String name) {
        return folder(name, "blog", "blog", null);
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
            final String f3_id = r.get(2).getValue("id").toString();
            job.execute(true).onComplete(context.asyncAssertSuccess(ra1 -> {
                folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(tutu -> {
                    context.assertEquals(3, tutu.result().size());
                });
                folderService.create(user, APPLICATION, f3_1.put("parentId", f3_id)).onComplete(context.asyncAssertSuccess(r2 -> {
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
                                    folderService.fetch(user, APPLICATION, Optional.of(f3_id)).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                        context.assertEquals(1, fetch2.size());
                                        final String id3_1 = fetch2.getJsonObject(0).getValue("_id").toString();
                                        final JsonArray a3_1 = fetch2.getJsonObject(0).getJsonArray("ancestors");
                                        final JsonArray ch3_1 = fetch2.getJsonObject(0).getJsonArray("childrenIds");
                                        context.assertEquals(r2, id3_1);
                                        context.assertEquals(2, a3_1.size());
                                        context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3_1.getString(0));
                                        context.assertEquals(f3_id, a3_1.getString(1));
                                        context.assertEquals(1, ch3_1.size());
                                        folderService.fetch(user, APPLICATION, Optional.of(id3_1)).onComplete(context.asyncAssertSuccess(fetch3 -> {
                                            context.assertEquals(1, fetch3.size());
                                            final String id3_1_1 = fetch3.getJsonObject(0).getValue("_id").toString();
                                            final JsonArray a3_1_1 = fetch3.getJsonObject(0).getJsonArray("ancestors");
                                            final JsonArray ch3_1_1 = fetch3.getJsonObject(0).getJsonArray("childrenIds");
                                            context.assertEquals(r3, id3_1_1);
                                            context.assertEquals(3, a3_1_1.size());
                                            context.assertEquals(ExplorerConfig.ROOT_FOLDER_ID, a3_1_1.getString(0));
                                            context.assertEquals(f3_id, a3_1_1.getString(1));
                                            context.assertEquals(id3_1, a3_1_1.getString(2));
                                            context.assertEquals(0, ch3_1_1.size());
                                            final Set<String> idToTrash = new HashSet<>(Arrays.asList(f3_id));
                                            //TRASH
                                            onTrash(context, user, async, idToTrash, id3_1);
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

    protected void onTrash(final TestContext context, final UserInfos user, final Async async, final Set<String> idToTrash, final String id3_1) {
        folderService.move(user, idToTrash, APPLICATION, Optional.of(ExplorerConfig.BIN_FOLDER_ID)).onComplete(context.asyncAssertSuccess(trashed -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r5 -> {
                folderService.fetch(user, APPLICATION, Optional.of(ExplorerConfig.BIN_FOLDER_ID)).onComplete(context.asyncAssertSuccess(fetch4 -> {
                    context.assertEquals(1, fetch4.size());
                    context.assertTrue(fetch4.getJsonObject(0).getBoolean("trashed"));
                    folderService.fetch(user, APPLICATION, Optional.of(id3_1)).onComplete(context.asyncAssertSuccess(fetch5 -> {
                        context.assertEquals(1, fetch5.size());
                        context.assertTrue(fetch5.getJsonObject(0).getBoolean("trashed"));
                        //UNTRASH
                        onUntrash(context, user, async, idToTrash, id3_1);
                    }));
                }));
            }));
        }));
    }

    protected void onUntrash(final TestContext context, final UserInfos user, final Async async, final Set<String> idToTrash, final String id3_1) {
        folderService.trash(user, idToTrash, APPLICATION, false).onComplete(context.asyncAssertSuccess(untrashed -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r6 -> {
                folderService.fetch(user, APPLICATION, Optional.of(ExplorerConfig.ROOT_FOLDER_ID)).onComplete(context.asyncAssertSuccess(fetch6 -> {
                    context.assertEquals(3, fetch6.size());
                    context.assertFalse(fetch6.getJsonObject(0).getBoolean("trashed"));
                    context.assertFalse(fetch6.getJsonObject(1).getBoolean("trashed"));
                    context.assertFalse(fetch6.getJsonObject(2).getBoolean("trashed"));
                    folderService.fetch(user, APPLICATION, Optional.of(id3_1)).onComplete(context.asyncAssertSuccess(fetch7 -> {
                        context.assertEquals(1, fetch7.size());
                        context.assertFalse(fetch7.getJsonObject(0).getBoolean("trashed"));
                        onDelete(context, user, async, idToTrash);
                    }));
                }));
            }));
        }));
    }

    protected void onDelete(final TestContext context, final UserInfos user, final Async async, final Set<String> idToTrash) {
        //DELETE
        folderService.fetch(user, APPLICATION, new FolderSearchOperation().setSearchEverywhere(true)).onComplete(context.asyncAssertSuccess(fetchBefore -> {
            context.assertEquals(5, fetchBefore.size());
            folderService.delete(user, APPLICATION, idToTrash).onComplete(context.asyncAssertSuccess(delete -> {
                job.execute(true).onComplete(context.asyncAssertSuccess(r7 -> {
                    folderService.fetch(user, APPLICATION, Optional.of(ExplorerConfig.ROOT_FOLDER_ID)).onComplete(context.asyncAssertSuccess(fetch8 -> {
                        context.assertEquals(2, fetch8.size());
                        folderService.fetch(user, APPLICATION, new FolderSearchOperation().setSearchEverywhere(true)).onComplete(context.asyncAssertSuccess(fetch9 -> {
                            context.assertEquals(2, fetch9.size());
                            async.complete();
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
        folderService.create(user, APPLICATION, Arrays.asList(f1, f2)).onComplete(context.asyncAssertSuccess(r -> {
            final String f2_id = r.get(1).getValue("id").toString();
            final Optional<String> source = Optional.of(f2_id);
            final Optional<String> dest = Optional.of(r.get(0).getValue("id").toString());
            job.execute(true).onComplete(context.asyncAssertSuccess(r4a -> {
                folderService.create(user, APPLICATION, f2_1.put("parentId", f2_id)).onComplete(context.asyncAssertSuccess(r2 -> {
                    job.execute(true).onComplete(context.asyncAssertSuccess(r4b -> {
                        f2.put("id", r2);
                        folderService.create(user, APPLICATION, f2_1_1.put("parentId", r2)).onComplete(context.asyncAssertSuccess(r3 -> {
                            job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                                folderService.fetch(user, APPLICATION, source).onComplete(context.asyncAssertSuccess(fetch0 -> {
                                    context.assertEquals(1, fetch0.size());
                                    folderService.fetch(user, APPLICATION, dest).onComplete(context.asyncAssertSuccess(fetch -> {
                                        context.assertEquals(0, fetch.size());
                                        final String f2Id = f2.getValue("id").toString();
                                        folderService.move(user, f2Id, APPLICATION, dest).onComplete(context.asyncAssertSuccess(move -> {
                                            job.execute(true).onComplete(context.asyncAssertSuccess(r5 -> {
                                                folderService.fetch(user, APPLICATION, dest).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                                    folderService.fetch(user, APPLICATION, source).onComplete(context.asyncAssertSuccess(fetch3 -> {
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
            }));
        }));
    }

    @Test
    public void shouldMoveIfOwner(TestContext context) {
        final JsonObject f1 = folder("move1");
        final UserInfos user = test.directory().generateUser("user_move1");
        final UserInfos user2 = test.directory().generateUser("user_move2");
        final Async async = context.async(2);
        folderService.create(user, APPLICATION, Collections.singletonList(f1)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r4a -> {
                folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch -> {
                    context.assertEquals(1, fetch.size());
                    final String fId = fetch.getJsonObject(0).getValue("_id").toString();
                    folderService.move(user2, fId, APPLICATION, Optional.empty()).onComplete(context.asyncAssertFailure(move -> {
                        context.assertEquals(move.getMessage(), "folder.move.id.invalid");
                        async.countDown();
                    }));
                    folderService.move(user, fId, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(move -> {
                        context.assertEquals(move.getValue("id").toString(), fId);
                        async.countDown();
                    }));
                }));
            }));
        }));
    }

    @Test
    public void shouldTrashIfOwner(TestContext context) {
        final JsonObject f1 = folder("trash1");
        final UserInfos user = test.directory().generateUser("user_trash1");
        final UserInfos user2 = test.directory().generateUser("user_trash2");
        final Async async = context.async(2);
        folderService.create(user, APPLICATION, Arrays.asList(f1)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r4a -> {
                folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch -> {
                    context.assertEquals(1, fetch.size());
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
                    }));
                }));
            }));
        }));
    }

    @Test
    public void shouldDeleteIfOwner(TestContext context) {
        final JsonObject f1 = folder("delete1");
        final UserInfos user = test.directory().generateUser("user_del1");
        final UserInfos user2 = test.directory().generateUser("user_del2");
        final Async async = context.async(2);
        folderService.create(user, APPLICATION, Arrays.asList(f1)).onComplete(context.asyncAssertSuccess(r -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r4a -> {
                folderService.fetch(user, APPLICATION, Optional.empty()).onComplete(context.asyncAssertSuccess(fetch -> {
                    context.assertEquals(1, fetch.size());
                    final String fId = fetch.getJsonObject(0).getValue("_id").toString();
                    final Set<String> ids = new HashSet<>();
                    ids.add(fId);
                    folderService.delete(user2, APPLICATION, ids).onComplete(context.asyncAssertFailure(move -> {
                        context.assertEquals(move.getMessage(), "folder.delete.id.invalid");
                        async.countDown();
                    }));
                    folderService.delete(user, APPLICATION, ids).onComplete(context.asyncAssertSuccess(move -> {
                        context.assertEquals(move.get(0), fId);
                        async.countDown();
                    }));
                }));
            }));
        }));
    }

}
