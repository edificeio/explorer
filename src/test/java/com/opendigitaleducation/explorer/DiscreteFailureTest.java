package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobErrorRule;
import com.opendigitaleducation.explorer.ingest.IngestJobErrorRuleBuilder;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Request;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.impl.ExplorerPluginClient;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.core.CompositeFuture.all;
import static java.util.Collections.singletonList;

@RunWith(VertxUnitRunner.class)
public class DiscreteFailureTest {
    private static final int BATCH_SIZE = 5;
    private static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static ElasticsearchContainer esContainer = test.database().createOpenSearchContainer().withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    @ClassRule
    public static MongoDBContainer mongoDBContainer = test.database().createMongoContainer().withReuse(true);
    @ClassRule
    public static GenericContainer redisContainer = new GenericContainer(DockerImageName.parse("redis:5.0.3-alpine")).withExposedPorts(6379);
    @Rule
    public Timeout timeoutRule = Timeout.seconds(360000);
    static ElasticClientManager elasticClientManager;
    static ResourceService resourceService;
    static FakeMongoPlugin plugin;
    static String application;
    static IngestJob job;
    static MongoClient mongoClient;
    static ExplorerPluginClient pluginClient;

    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        test.database().initMongo(context, mongoDBContainer);
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final String resourceIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
        System.out.println("Using index: " + resourceIndex);
        ExplorerConfig.getInstance().setEsIndex(FakeMongoPlugin.FAKE_APPLICATION, resourceIndex);
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final JsonObject mongoConfig = new JsonObject().put("connection_string", mongoDBContainer.getReplicaSetUrl());
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        final RedisClient redisClient = new RedisClient(test.vertx(), redisConfig);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(test.vertx(), postgresClient);
        mongoClient = MongoClient.createShared(test.vertx(), mongoConfig);
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, communication, postgresClient);
        plugin = FakeMongoPlugin.withRedisStream(test.vertx(), redisClient, mongoClient);
        application = plugin.getApplication();
        final Async async = context.async();
        final Promise<Void> promiseMongo = Promise.promise();
        final Promise<Void> promiseRedis = Promise.promise();
        all(Arrays.asList(promiseRedis.future(), promiseRedis.future())).onComplete(e -> async.complete());
        createMapping(elasticClientManager, context, resourceIndex).onComplete(r -> promiseMongo.complete());
        final JsonObject jobConf = new JsonObject()
                .put("error-rules-allowed", true)
                .put("batch-size", BATCH_SIZE)
                .put("max-delay-ms", 2000);
        pluginClient = IExplorerPluginClient.withBus(test.vertx(), FakeMongoPlugin.FAKE_APPLICATION, FakeMongoPlugin.FAKE_TYPE);
        final JsonObject rights = new JsonObject();
        rights.put(ExplorerConfig.RIGHT_READ, ExplorerConfig.RIGHT_READ);
        rights.put(ExplorerConfig.RIGHT_CONTRIB, ExplorerConfig.RIGHT_CONTRIB);
        rights.put(ExplorerConfig.RIGHT_MANAGE, ExplorerConfig.RIGHT_MANAGE);
        ExplorerConfig.getInstance().addRightsForApplication(FakeMongoPlugin.FAKE_APPLICATION, rights);
        //flush redis
        redisClient.getClient().send(Request.cmd(Command.FLUSHALL), e -> {
            final MessageReader reader = MessageReader.redis(redisClient, redisConfig);
            job = IngestJob.create(test.vertx(), elasticClientManager, postgresClient, jobConf, reader);
            //start job to create streams
            job.start().compose(ee -> job.stop())
                .onComplete(context.asyncAssertSuccess(eee -> promiseRedis.complete()));
        });
    }


    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    static JsonObject resource(final String name) {
        return new JsonObject().put("name", name);
    }

    /**
     * GOAL : Test that no old message ever rewrites a fresher one.
     *
     * STEPS :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Update the resource 3 times. The second message is designed to fail.</li>
     *     <li>Verify that the resource is whether at a version prior to the error or at the last valid version
     *     (both are functionally okay and depend entirely on the implementation and the configuration).</li>
     *     <li>Deactivate the rules.</li>
     *     <li>Launch the job</li>
     *     <li>Verify that the resource has the desired end state.</li>
     * </ol>
     * @param context Test context
     */
    @Test
    public void oldMessagesShouldNotRewriteNewOne(TestContext context) {
        final JsonObject f1 = resource("folder1");
        f1.put("content", "initial");
        final UserInfos user = test.directory().generateUser("usermove");
        final Async async = context.async();
        resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
            context.assertEquals(0, fetch0.size());
            plugin.create(user, singletonList(f1), false).onComplete(context.asyncAssertSuccess(r -> {
                executeJobNTimesAndFetchUniqueResult(1, user, context).compose(createdResource -> {
                    ////////////////////////////
                    // Generate update messages
                    final List<JsonObject> modifications = new ArrayList<>();
                    final int nbFailedMessages = 2;
                    final String expectedFinalMessage = "after first error message";
                    modifications.addAll(generateModifiedResourcesToSucceed(createdResource, 1, "before error messages"));
                    modifications.addAll(generateModifiedResourcesToFail(createdResource, nbFailedMessages));
                    modifications.addAll(generateModifiedResourcesToSucceed(createdResource, 1, expectedFinalMessage));
                    activateErrorRules();
                    return pluginNotifyUpsert(user, modifications).onComplete(context.asyncAssertSuccess(r2 -> {
                        ////////////////////////////
                        // Launch the job n times to make sure that upon restart nothing changes
                        executeJobNTimesAndFetchUniqueResult(10, user, context).onComplete(context.asyncAssertSuccess(asReturnedByFetch -> {
                            ////////////////////////////
                            // Verify that the desired state has been reached or that the error messages
                            // were not processed
                            final String contentOfMessage = asReturnedByFetch.getString("content", "");
                            final String myFlag = asReturnedByFetch.getString("my_flag");
                            context.assertTrue(contentOfMessage.contains("initial") ||
                                            contentOfMessage.contains("before error messages") ||
                                            contentOfMessage.contains("after first error message"),
                                    "The resource should be at a valid version before or after the version but not at the invalid one. Instead it was " + contentOfMessage);
                            ////////////////////////////
                            // Clear error rules and relaunch the job
                            clearErrorRules();
                            executeJobNTimesAndFetchUniqueResult(10, user, context).onComplete(context.asyncAssertSuccess(finalResult -> {
                                context.assertEquals("after first error message0", finalResult.getString("content"),
                                        "The resource should be at a valid version before or after the version but not at the invalid one");
                                context.assertFalse(finalResult.containsKey("my_flag"), "The final version of the document should not contain a flag but it was instead " + finalResult.getString("my_flag"));
                                async.complete();
                            }));
                        }));
                    }));
                });
            }));
        }));
    }

    private Future<Void> pluginNotifyUpsert(UserInfos user, List<JsonObject> modifications) {
        return pluginNotifyUpsert(user, modifications, 0);
    }
    private Future<Void> pluginNotifyUpsert(UserInfos user, List<JsonObject> modifications, final int position) {
        final Future<Void> done;
        if(modifications == null || modifications.isEmpty() || position >= modifications.size()) {
            done = Future.succeededFuture();
        } else {
            done = plugin.notifyUpsert(user, modifications.get(position))
                    .compose(e -> pluginNotifyUpsert(user, modifications, position + 1));
        }
        return done;
    }

    private Future<JsonObject> executeJobNTimesAndFetchUniqueResult(final int nbBatchExecutions, final UserInfos user, final TestContext context) {
        return executeJobNTimes(nbBatchExecutions, context).flatMap(e ->
            resourceService.fetch(user, application, new ResourceSearchOperation())
            .map(results -> {
                context.assertEquals(1, results.size());
                return results.getJsonObject(0);
            })
        );
    }

    private void activateErrorRules() {
        job.setErrorRules(evictionRule("content", ".*fail.*"));
        job.setErrorRules(evictionRule("my_flag", ".*fail.*"));
    }

    private void clearErrorRules() {
        job.setErrorRules(Collections.emptyList());
    }

    private Future<Object> executeJobNTimes(int nbTimesToExecute, final TestContext context) {
        final Future<Object> onDone;
        if (nbTimesToExecute <= 0) {
            onDone = Future.succeededFuture();
        } else {
            onDone = job.execute(true).compose(e -> executeJobNTimes(nbTimesToExecute - 1, context));
        }
        return onDone.onFailure(e -> context.asyncAssertFailure());
    }

    private List<JsonObject> generateModifiedResourcesToFail(JsonObject originalResource, final int numberOfMessages) {
        return IntStream.range(0, numberOfMessages).mapToObj(i -> {
            final JsonObject modifiedResource = originalResource.copy();
            modifiedResource.put("content", "modified for failure number " + i);
            modifiedResource.put("my_flag", "fail " + i);
            modifiedResource.put("_id", originalResource.getString("assetId"));
            return modifiedResource;
        }).collect(Collectors.toList());
    }

    private List<JsonObject> generateModifiedResourcesToSucceed(JsonObject originalResource, int numberOfMessages,
                                                                final String messagePrefix) {
        final String prefix = messagePrefix == null ? "modified for success number " : messagePrefix;
        return IntStream.range(0, numberOfMessages).mapToObj(i -> {
            final JsonObject modifiedResource = originalResource.copy();
            modifiedResource.put("content", prefix + i);
            modifiedResource.put("_id", originalResource.getString("assetId"));
            return modifiedResource;
        }).collect(Collectors.toList());
    }

    private List<IngestJobErrorRule> evictionRule(final String attributeName, final String attributeValue) {
        final List<IngestJobErrorRule> rules = new ArrayList<>();
        rules.add(new IngestJobErrorRuleBuilder()
                .withValueToTarget(attributeName, attributeValue)
                .createIngestJobErrorRule());
        return rules;
    }

}
