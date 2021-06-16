package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginClient;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginClientDefault;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunication;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunicationPostgres;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

@RunWith(VertxUnitRunner.class)
public class MongoPluginTest {
    private static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.9.0").withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static MongoDBContainer mongoDBContainer = test.database().createMongoContainer().withReuse(true);

    static ElasticClientManager elasticClientManager;
    static ResourceService resourceService;
    static FakeMongoPlugin plugin;
    static String application;
    static IngestJob job;
    static MongoClient mongoClient;
    static ExplorerPluginClient pluginClient;

    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final String resourceIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
        System.out.println("Using index: " + resourceIndex);
        ExplorerConfig.getInstance().setEsIndex(FakeMongoPlugin.FAKE_APPLICATION, resourceIndex);
        final JsonObject mongoConfig = new JsonObject().put("connection_string", mongoDBContainer.getReplicaSetUrl());
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        ExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(test.vertx(), postgresClient);
        mongoClient = MongoClient.createShared(test.vertx(), mongoConfig);
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, communication, postgresClient);
        plugin = FakeMongoPlugin.withPostgresChannel(test.vertx(), postgresClient, mongoClient);
        application = plugin.getApplication();
        final Async async = context.async();
        createMapping(elasticClientManager, context, resourceIndex).onComplete(r -> async.complete());
        final MessageReader reader = MessageReader.postgres(postgresClient, new JsonObject());
        job = IngestJob.create(test.vertx(), elasticClientManager, postgresClient, new JsonObject(), reader);
        pluginClient = ExplorerPluginClient.withBus(test.vertx(), FakeMongoPlugin.FAKE_APPLICATION, FakeMongoPlugin.FAKE_TYPE);
    }


    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    static JsonObject resource(final String name) {
        return new JsonObject().put("name", name);
    }

    @Test
    public void shouldCreateResource(TestContext context) {
        final JsonObject f1 = resource("folder1");
        final JsonObject f2 = resource("folder2");
        final JsonObject f3 = resource("folder3");
        final UserInfos user = test.directory().generateUser("usermove");
        final Async async = context.async();
        resourceService.fetch(user, application, new ResourceService.SearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
            context.assertEquals(0, fetch0.size());
            plugin.create(user, Arrays.asList(f1, f2, f3), false).onComplete(context.asyncAssertSuccess(r -> {
                job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                    resourceService.fetch(user, application, new ResourceService.SearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                        context.assertEquals(3, fetch1.size());
                        async.complete();
                    }));
                }));
            }));
        }));
    }


    @Test
    public void shouldReindexResource(TestContext context) {
        final UserInfos user = test.directory().generateUser("reindex");
        final JsonObject f1 = resource("reindex1").put("creatorId", user.getUserId()).put("_id", "reindex1");
        final JsonObject f2 = resource("reindex2").put("creatorId", user.getUserId()).put("_id", "reindex2");
        final JsonObject f3 = resource("reindex3").put("creatorId", user.getUserId()).put("_id", "reindex3");
        final Promise p1 = Promise.promise();
        final Promise p2 = Promise.promise();
        final Promise p3 = Promise.promise();
        mongoClient.insert(FakeMongoPlugin.COLLECTION, f1, p1);
        mongoClient.insert(FakeMongoPlugin.COLLECTION, f2, p2);
        mongoClient.insert(FakeMongoPlugin.COLLECTION, f3, p3);
        final Async async = context.async();
        plugin.start();
        CompositeFuture.all(p1.future(), p2.future(), p3.future()).onComplete(context.asyncAssertSuccess(r1 -> {
            job.execute(true).onComplete(context.asyncAssertSuccess(r0 -> {
                resourceService.fetch(user, application, new ResourceService.SearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
                    context.assertEquals(0, fetch0.size());
                    pluginClient.getForIndexation(user, Optional.empty(), Optional.empty()).onComplete(context.asyncAssertSuccess(r2 -> {
                        plugin.getCommunication().waitPending().onComplete(context.asyncAssertSuccess(r3 -> {
                            job.execute(true).onComplete(context.asyncAssertSuccess(r4 -> {
                                job.waitPending().onComplete(context.asyncAssertSuccess(r5 -> {
                                    resourceService.fetch(user, application, new ResourceService.SearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                                        context.assertEquals(3, fetch1.size());
                                        async.complete();
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
