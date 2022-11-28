package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.ingest.impl.ErrorMessageTransformer;
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
import org.junit.*;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    static  RedisClient redisClient;
    static String application;
    static IngestJob job;
    static MongoClient mongoClient;
    static ExplorerPluginClient pluginClient;
    static AtomicInteger idtResource = new AtomicInteger(0);
    static AtomicInteger indexMessage = new AtomicInteger(0);

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
        redisClient = new RedisClient(test.vertx(), redisConfig);
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
                .put("max-delay-ms", 2000)
                .put("message-merger", "default");
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

    @Before
    public void beforeTests(TestContext context){
        System.out.println("Flushing data");
        clearErrorRules();
        //flush redis
        /*final Async async = context.async();
        redisClient.getClient().send(Request.cmd(Command.FLUSHALL), e -> {
            async.complete();
        });*/
    }


    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    static JsonObject resource(final String name) {
        return new JsonObject().put("name", name);
    }

    /**
     * <u>GOAL</u> : Test that no old message ever rewrites a fresher one.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules</li>
     *     <li>Update the resource n times (with n small enough to hold everything in one batch). The messages in between
     *     are designed to fail</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource is whether at a version prior to the error or at the last valid version
     *     (both are functionally okay and depend entirely on the implementation and the configuration).</li>
     *     <li>Deactivate error rules</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource has the desired end state.</li>
     * </ol>
     * @param context Test context
     */
    @Test
    public void oldMessagesShouldNotRewriteNewOneWhenMessagesInOneBatchErrorInES(TestContext context) {
        testInterspersedErrorMessages(1, 2, 1, createErrorRulesForES(), context);
    }

    /**
     * <u>GOAL</u> : Test that no old message ever rewrites a fresher one.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules</li>
     *     <li>Update the resource n times (with n small enough to hold everything in one batch). The messages in between
     *     are designed to fail</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource is whether at a version prior to the error or at the last valid version
     *     (both are functionally okay and depend entirely on the implementation and the configuration).</li>
     *     <li>Deactivate error rules</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource has the desired end state.</li>
     * </ol>
     * @param context Test context
     */
    @Test
    public void oldMessagesShouldNotRewriteNewOneWhenMessagesInOneBatchErrorInPG(TestContext context) {
        testInterspersedErrorMessages(1, 2, 1, createErrorRulesForPG(), context);
    }
    /**
     * <u>GOAL</u> : Test that no old message ever rewrites a fresher one across multiple batches of data.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules</li>
     *     <li>Update the resource n times (with n small enough to hold everything in one batch). The messages in between
     *     are designed to fail</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource is whether at a version prior to the error or at the last valid version
     *     (both are functionally okay and depend entirely on the implementation and the configuration).</li>
     *     <li>Deactivate error rules</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource has the desired end state.</li>
     * </ol>
     * @param context Test context
     */
    @Test
    public void oldMessagesShouldNotRewriteNewOneWhenMessagesInMultipleBatchesErrorInES(TestContext context) {
        testInterspersedErrorMessages(2, BATCH_SIZE * 5, BATCH_SIZE + 1, createErrorRulesForES(), context);
    }
    /**
     * <u>GOAL</u> : Test that no old message ever rewrites a fresher one across multiple batches of data.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules</li>
     *     <li>Update the resource n times (with n small enough to hold everything in one batch). The messages in between
     *     are designed to fail</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource is whether at a version prior to the error or at the last valid version
     *     (both are functionally okay and depend entirely on the implementation and the configuration).</li>
     *     <li>Deactivate error rules</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource has the desired end state.</li>
     * </ol>
     * @param context Test context
     */
    @Test
    public void oldMessagesShouldNotRewriteNewOneWhenMessagesInMultipleBatchesErrorInPG(TestContext context) {
        testInterspersedErrorMessages(2, BATCH_SIZE * 5, BATCH_SIZE + 1, createErrorRulesForPG(), context);
    }
    public void testInterspersedErrorMessages(final int nbFirstMessagesOk,
                                              final int nbMessagesKO,
                                              final int nbLastMessagesOk,
                                              final List<ErrorMessageTransformer.IngestJobErrorRule> errors,
                                              final TestContext context) {
        final String resourceName = "resource" + idtResource.incrementAndGet();
        final JsonObject f1 = resource(resourceName);
        f1.put("content", "initial");
        final UserInfos user = test.directory().generateUser("usermove");
        final Async async = context.async();
        final int nbTimesToExecuteJob = 2 * (nbLastMessagesOk + nbMessagesKO + nbLastMessagesOk);
        resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
            context.assertTrue(
                    fetch0.stream().noneMatch(resource -> ((JsonObject)resource).getString("name", "").equals(f1.getString("name"))),
                    "The user already had a resource called " + f1.getString("name")
            );
            plugin.create(user, singletonList(f1), false).onComplete(context.asyncAssertSuccess(r -> {
                executeJobNTimesAndFetchUniqueResult(1, user, resourceName, context).compose(createdResource -> {
                    ////////////////////////////
                    // Generate update messages
                    final List<JsonObject> modifications = new ArrayList<>();
                    final String expectedFinalMessage = "after first error message";
                    modifications.addAll(generateModifiedResourcesToSucceed(createdResource, nbFirstMessagesOk, "before error messages"));
                    modifications.addAll(generateModifiedResourcesToFail(createdResource, nbMessagesKO));
                    modifications.addAll(generateModifiedResourcesToSucceed(createdResource, nbLastMessagesOk, expectedFinalMessage));
                    setErrorRules(errors);
                    return pluginNotifyUpsert(user, modifications).onComplete(context.asyncAssertSuccess(r2 -> {
                        ////////////////////////////
                        // Launch the job n times to make sure that upon restart nothing changes
                        executeJobNTimesAndFetchUniqueResult(nbTimesToExecuteJob, user, resourceName, context).onComplete(context.asyncAssertSuccess(asReturnedByFetch -> {
                            ////////////////////////////
                            // Verify that the desired state has been reached or that the error messages
                            // were not processed
                            final String contentOfMessage = asReturnedByFetch.getString("content", "");
                            context.assertTrue(contentOfMessage.contains("initial") ||
                                            contentOfMessage.contains("before error messages") ||
                                            contentOfMessage.contains("after first error message"),
                                    "The resource should be at a valid version before or after the version but not at the invalid one. Instead it was " + contentOfMessage);
                            ////////////////////////////
                            // Clear error rules and relaunch the job
                            clearErrorRules();
                            executeJobNTimesAndFetchUniqueResult(nbTimesToExecuteJob, user, resourceName, context).onComplete(context.asyncAssertSuccess(finalResult -> {
                                context.assertEquals(modifications.get(modifications.size() - 1).getString("content"), finalResult.getString("content"),
                                        "The resource should be at a valid version before or after the version but not at the invalid one");
                                // TODO JBE this limitation comes from the fact that I manually add a field that is not supposed to be present on the source
                                // and that an upsert in ES does not remove fields
                                //context.assertFalse(finalResult.containsKey("my_flag"), "The final version of the document should not contain a flag but it was instead " + finalResult.getString("my_flag"));
                                async.complete();
                            }));
                        }));
                    }));
                });
            }));
        }));
    }

    private void setErrorRules(List<ErrorMessageTransformer.IngestJobErrorRule> errors) {
        job.getMessageTransformer()
                .clearChain()
                .withTransformer(new ErrorMessageTransformer(errors));
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

    private Future<JsonObject> executeJobNTimesAndFetchUniqueResult(final int nbBatchExecutions, final UserInfos user,
                                                                    final String resourceName, final TestContext context) {
        return executeJobNTimes(nbBatchExecutions, context).flatMap(e ->
            resourceService.fetch(user, application, new ResourceSearchOperation())
            .map(results -> {
                final List<JsonObject> resultsForMyResource = results.stream().map(r -> ((JsonObject)r))
                    .filter(r -> r.getString("name", "").equals(resourceName))
                    .collect(Collectors.toList());
                context.assertEquals(1, resultsForMyResource.size());
                return resultsForMyResource.get(0);
            })
        );
    }

    private List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForES() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRuleES("my_flag", ".*fail.*");
        errors.addAll(evictionRuleES("content", ".*fail.*"));
        return errors;
    }
    private List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForPG() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRulePG("my_flag", ".*fail.*");
        errors.addAll(evictionRulePG("content", ".*fail.*"));
        return errors;
    }

    private void clearErrorRules() {
        job.getMessageTransformer().clearChain();
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
            final int idxMessage = indexMessage.incrementAndGet();
            modifiedResource.put("content", "modified for failure number " + idxMessage);
            modifiedResource.put("my_flag", "fail " + idxMessage);
            modifiedResource.put("_id", originalResource.getString("assetId"));
            return modifiedResource;
        }).collect(Collectors.toList());
    }

    private List<JsonObject> generateModifiedResourcesToSucceed(JsonObject originalResource, int numberOfMessages,
                                                                final String messagePrefix) {
        final String prefix = messagePrefix == null ? "modified for success number " : messagePrefix;
        return IntStream.range(0, numberOfMessages).mapToObj(i -> {
            final JsonObject modifiedResource = originalResource.copy();
            modifiedResource.put("content", prefix + indexMessage.incrementAndGet());
            modifiedResource.put("_id", originalResource.getString("assetId"));
            return modifiedResource;
        }).collect(Collectors.toList());
    }

    private List<ErrorMessageTransformer.IngestJobErrorRule> evictionRuleES(final String attributeName, final String attributeValue) {
        return evictionRules(attributeName, attributeValue, "es");
    }

    private List<ErrorMessageTransformer.IngestJobErrorRule> evictionRulePG(final String attributeName, final String attributeValue) {
        return evictionRules(attributeName, attributeValue, "pg-ingest");
    }

    private List<ErrorMessageTransformer.IngestJobErrorRule> evictionRules(final String attributeName, final String attributeValue, final String pointOfFailure) {
        final List<ErrorMessageTransformer.IngestJobErrorRule> rules = new ArrayList<>();
        rules.add(new ErrorMessageTransformer.IngestJobErrorRuleBuilder()
                .withValueToTarget(attributeName, attributeValue)
                .setPointOfFailure(pointOfFailure)
                .createIngestJobErrorRule());
        return rules;
    }

}
