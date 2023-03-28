package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.ingest.impl.ErrorMessageTransformer;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.DefaultMuteService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import com.opendigitaleducation.explorer.tests.ExplorerTestHelper;
import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.executeJobNTimesAndFetchUniqueResult;
import static io.vertx.core.CompositeFuture.all;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerPluginMetricsFactory;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.IExplorerPluginMetricsRecorder;
import org.entcore.common.explorer.impl.ExplorerPluginClient;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.share.ShareRoles;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    public static GenericContainer redisContainer = test.database().createRedisContainer().withExposedPorts(6379);
    @Rule
    public Timeout timeoutRule = Timeout.seconds(360000);
    static ElasticClientManager elasticClientManager;
    static ResourceService resourceService;
    static FakeMongoPlugin plugin;
    static FaillibleRedisClient redisClient;
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
        IngestJobMetricsRecorderFactory.init(test.vertx(), new JsonObject());
        ExplorerPluginMetricsFactory.init(test.vertx(), new JsonObject());
        ExplorerConfig.getInstance().setEsIndex(FakeMongoPlugin.FAKE_APPLICATION, resourceIndex);
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final JsonObject mongoConfig = new JsonObject().put("connection_string", mongoDBContainer.getReplicaSetUrl());
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        redisClient = new FaillibleRedisClient(test.vertx(), redisConfig);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(test.vertx(), postgresClient, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        mongoClient = MongoClient.createShared(test.vertx(), mongoConfig);
        final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, communication, postgresClient, muteService);
        plugin = FakeMongoPlugin.withRedisStream(test.vertx(), redisClient, mongoClient);
        application = plugin.getApplication();
        final Async async = context.async();
        final Promise<Void> promiseMongo = Promise.promise();
        final Promise<Void> promiseRedis = Promise.promise();
        final Promise<Void> promiseScript = Promise.promise();
        all(Arrays.asList(promiseRedis.future(), promiseRedis.future(), promiseScript.future())).onComplete(e -> async.complete());
        ExplorerTestHelper.createMapping(test.vertx(), elasticClientManager, resourceIndex).onComplete(r -> promiseMongo.complete());
        createScript(test.vertx(), elasticClientManager).onComplete(r -> promiseScript.complete());
        final JsonObject jobConf = new JsonObject()
                .put("error-rules-allowed", true)
                .put("batch-size", BATCH_SIZE)
                .put("max-delay-ms", 2000)
                .put("message-merger", "noop")
                .put("opensearch-options", new JsonObject().put("wait-for", true));
        pluginClient = IExplorerPluginClient.withBus(test.vertx(), FakeMongoPlugin.FAKE_APPLICATION, FakeMongoPlugin.FAKE_TYPE);
        //flush redis
        redisClient.getClient().flushall(new ArrayList<>(), e -> {
            final MessageReader reader = MessageReader.redis(redisClient, redisConfig);
            job = IngestJob.createForTest(test.vertx(), elasticClientManager, postgresClient, jobConf, reader);
            //start job to create streams
            job.start().compose(ee -> job.stop())
                .onComplete(context.asyncAssertSuccess(eee -> promiseRedis.complete()));
        });
    }

    @Before
    public void beforeTests(TestContext context){
        System.out.println("Flushing data");
        clearErrorRules();
    }

    static JsonObject resource(final String name, final String creatorId) {
        return new JsonObject().put("name", name).put("version", 1).put("creatorId", creatorId);
    }

    static JsonObject resource(final String name, final UserInfos creator) {
        return resource(name, creator.getUserId());
    }

    /**
     * <u>GOAL</u> : Test that no old message (coming from a communication error with ES) ever rewrites a fresher one.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules to generate an error while saving in ES</li>
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
    public void testOldMessagesShouldNotRewriteNewOneWhenMessagesInOneBatchErrorInES(TestContext context) {
        testInterspersedErrorMessages(1, 2, 1, createErrorRulesForES(), context);
    }

    /**
     * <u>GOAL</u> : Test that no old message (coming from a communication error with Postgre) ever rewrites a fresher one.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules to generate an error when communicating with PG</li>
     *     <li>Update the resource :
     *      <ul>
     *         <li>n times (with n small enough to hold everything in one batch) in an OK version</li>
     *         <li>n times to fail</li>
     *         <li>n times (with n small enough to hold everything in one batch) in an OK version</li>
     *      </ul>
     *     </li>
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
    public void testOldMessagesShouldNotRewriteNewOneWhenMessagesInOneBatchErrorInPG(TestContext context) {
        testInterspersedErrorMessages(1, 2, 1, createErrorRulesForPG(), context);
    }
    /**
     * <u>GOAL</u> : Test that no old message (coming from a communication error with ES) ever rewrites a fresher one across multiple batches of data.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules to generate an error while communicating with ES</li>
     *     <li>Update the resource :
     *      <ul>
     *         <li>n times (with n small enough to hold everything in one batch) in an OK version</li>
     *         <li>n times to fail</li>
     *         <li>n times (with n small enough to hold everything in one batch) in an OK version</li>
     *      </ul>
     *     </li>
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
    public void testOldMessagesShouldNotRewriteNewOneWhenMessagesInMultipleBatchesErrorInES(TestContext context) {
        testInterspersedErrorMessages(2, BATCH_SIZE + 1, BATCH_SIZE + 1, createErrorRulesForES(), context);
    }
    /**
     * <u>GOAL</u> : Test that no old message (coming from a communication error with Postgre) ever rewrites a fresher one across multiple batches of data.
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
    public void testOldMessagesShouldNotRewriteNewOneWhenMessagesInMultipleBatchesErrorInPG(TestContext context) {
        testInterspersedErrorMessages(2, BATCH_SIZE + 1, BATCH_SIZE + 1, createErrorRulesForPG(), context);
    }


    /**
     * <u>GOAL</u> : Test that even if a redis message cannot be written to Redis at first its content can finally be
     * processed by the IngestJob when Redis is finally back online.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules on REDIS</li>
     *     <li>Update the resource n times to fail</li>
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
    public void testMessagesThatCouldNotBeWrittenInRedisAreEventuallyIngested(TestContext context) {
        errorOnRedis(1, 2, 0, createErrorRulesForRedisXAdd(), context);
    }


        /**
     * <u>GOAL</u> : Test that the final result of the ingestion is the expected one even if two (or more) messages
     * do not arrive in the right order.
     *
     * <u>STEPS</u> :
     * <ol>
     *     <li>Create a resource</li>
     *     <li>Activate error rules on REDIS</li>
     *     <li>Update the resource at time t by changing the content</li>
     *     <li>Update the resource at time t + 1  by changing again the content</li>
     *     <li>Configure the transformer to invert the messages</li>
     *     <li>Launch the job x times</li>
     *     <li>Verify that the resource has the desired end state.</li>
     * </ol>
     * @param context Test context
     */
    @Test
    public void testMessagesThatArriveInWrongOrderAreCorrectlyProcessed(TestContext context) {
        plugin.start();
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRules("my_flag", ".*fail.*", "redis-read", "HEAD");
        errorOnRedisOnContent(BATCH_SIZE, errors, context);
    }

    public void errorOnRedis(final int nbFirstMessagesOk,
                             final int nbMessagesKO,
                             final int nbLastMessagesOk,
                             final List<ErrorMessageTransformer.IngestJobErrorRule> errors,
                             final TestContext context) {
        final String resourceName = "resource" + idtResource.incrementAndGet();
        final UserInfos user = test.directory().generateUser("usermove");
        final JsonObject f1 = resource(resourceName, user);
        f1.put("content", "initial");
        final Async async = context.async();
        final int nbMessages = nbLastMessagesOk + nbMessagesKO + nbLastMessagesOk;
        final int nbTimesToExecuteJob = 2 * nbMessages;
        resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
            context.assertTrue(
                    fetch0.stream().noneMatch(resource -> ((JsonObject)resource).getString("name", "").equals(f1.getString("name"))),
                    "The user already had a resource called " + f1.getString("name")
            );
            plugin.create(user, singletonList(f1), false).onComplete(context.asyncAssertSuccess(r -> {
                executeJobNTimesAndFetchUniqueResult(job, 1, application, resourceService, user, resourceName, context).compose(createdResource -> {
                    ////////////////////////////
                    // Generate update messages
                    final List<JsonObject> modifications = new ArrayList<>();
                    final String expectedFinalMessage = "after first error message";
                    modifications.addAll(generateModifiedResourcesToSucceed(createdResource, nbFirstMessagesOk, "before error messages"));
                    modifications.addAll(generateModifiedResourcesToFail(createdResource, nbMessagesKO));
                    modifications.addAll(generateModifiedResourcesToSucceed(createdResource, nbLastMessagesOk, expectedFinalMessage));
                    redisClient.setErrorRules(errors);
                    return pluginNotifyUpsert(user, modifications).onComplete(context.asyncAssertSuccess(r2 -> {
                        ////////////////////////////
                        // Launch the job n times to make sure that upon restart nothing changes
                        executeJobNTimesAndFetchUniqueResult(job, nbTimesToExecuteJob, application, resourceService, user, resourceName, context).onComplete(context.asyncAssertSuccess(asReturnedByFetch -> {
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
                            redisClient.setErrorRules(emptyList());
                            executeJobNTimesAndFetchUniqueResult(job, nbTimesToExecuteJob, application, resourceService, user, resourceName, context).onComplete(context.asyncAssertSuccess(finalResult -> {
                                context.assertEquals(modifications.get(modifications.size() - 1).getString("content"), finalResult.getString("content"),
                                        "The resource should be at a valid version before or after the version but not at the invalid one");
                                // This limitation comes from the fact that I manually add a field that is not supposed to be present on the source
                                // and that an upsert in ES does not remove fields
                                //context.assertFalse(finalResult.containsKey("my_flag"), "The final version of the document should not contain a flag but it was instead " + finalResult.getString("my_flag"));
                                final JsonArray subresources = finalResult.getJsonArray("subresources", new JsonArray());
                                final Set<String> srContents = subresources.stream().map(sr -> ((JsonObject) sr).getString("contentHtml")).collect(Collectors.toSet());
                                context.assertEquals(nbMessages, srContents.size(), "There should be exactly one sub resource per message. What we got back is " + srContents);
                                async.complete();
                            }));
                        }));
                    }));
                });
            }));
        }));
    }

    public void errorOnRedisOnContent(
            final int nbMessages,
            final List<ErrorMessageTransformer.IngestJobErrorRule> errors,
            final TestContext context) {
        final String resourceName = "resource" + idtResource.incrementAndGet();
        final UserInfos user = test.directory().generateUser("usermove");
        final JsonObject f1 = resource(resourceName, user);
        f1.put("content", "initial");
        final Async async = context.async();
        final int nbTimesToExecuteJob = 2 * nbMessages;
        resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
            context.assertTrue(
                    fetch0.stream().noneMatch(resource -> ((JsonObject)resource).getString("name", "").equals(f1.getString("name"))),
                    "The user already had a resource called " + f1.getString("name")
            );
            plugin.create(user, singletonList(f1), false).onComplete(context.asyncAssertSuccess(r -> {
                executeJobNTimesAndFetchUniqueResult(job, 1, application, resourceService, user, resourceName, context).compose(createdResource -> {
                    ////////////////////////////
                    // Generate update messages
                    final List<JsonObject> modifications = new ArrayList<>();
                    modifications.addAll(generateModifiedResourcesToSucceed(createdResource, nbMessages - 1, "before invertion messages"));
                    modifications.addAll(generateModifiedResourcesToFail(createdResource, 1));
                    final String expectedFinalMessage = modifications.get(modifications.size() - 1).getString("content");
                    final long expectedVersion = modifications.get(modifications.size() - 1).getLong("version");
                    setErrorRules(errors);
                    return pluginNotifyUpsert(user, modifications).onComplete(context.asyncAssertSuccess(r2 -> {
                        ////////////////////////////
                        // Launch the job n times to make sure that upon restart nothing changes
                        executeJobNTimesAndFetchUniqueResult(job, nbTimesToExecuteJob, application, resourceService, user, resourceName, context).onComplete(context.asyncAssertSuccess(asReturnedByFetch -> {
                            ////////////////////////////
                            // Verify that the desired state has been reached or that the error messages
                            // were not processed
                            final String contentOfMessage = asReturnedByFetch.getString("content", "");
                            final Long version = asReturnedByFetch.getLong("version", -1L);
                            context.assertEquals(expectedFinalMessage, contentOfMessage,
                                    "The resource should have the last content. Instead it was " + contentOfMessage);
                            context.assertEquals(expectedVersion, asReturnedByFetch.getLong("version", -1l),
                                    "The resource should have the last version. Instead it was " + version);
                            async.complete();
                        }));
                    }));
                });
            }));
        }));
    }
    public void testInterspersedErrorMessages(final int nbFirstMessagesOk,
                                              final int nbMessagesKO,
                                              final int nbLastMessagesOk,
                                              final List<ErrorMessageTransformer.IngestJobErrorRule> errors,
                                              final TestContext context) {
        final String resourceName = "resource" + idtResource.incrementAndGet();
        final UserInfos user = test.directory().generateUser("usermove");
        final JsonObject f1 = resource(resourceName, user);
        f1.put("content", "initial");
        final Async async = context.async();
        final int nbMessages = nbFirstMessagesOk + nbMessagesKO + nbLastMessagesOk;
        final int nbTimesToExecuteJob = 5;
        resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
            context.assertTrue(
                    fetch0.stream().noneMatch(resource -> ((JsonObject)resource).getString("name", "").equals(f1.getString("name"))),
                    "The user already had a resource called " + f1.getString("name")
            );
            plugin.create(user, singletonList(f1), false).onComplete(context.asyncAssertSuccess(r -> {
                executeJobNTimesAndFetchUniqueResult(job, 1, application, resourceService, user, resourceName, context).compose(createdResource -> {
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
                        executeJobNTimesAndFetchUniqueResult(job, nbTimesToExecuteJob, application, resourceService, user, resourceName, context).onComplete(context.asyncAssertSuccess(asReturnedByFetch -> {
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
                            executeJobNTimesAndFetchUniqueResult(job, nbTimesToExecuteJob, application, resourceService, user, resourceName, context).onComplete(context.asyncAssertSuccess(finalResult -> {
                                context.assertEquals(modifications.get(modifications.size() - 1).getString("content"), finalResult.getString("content"),
                                        "The resource should be at a valid version");
                                // TODO JBE this limitation comes from the fact that I manually add a field that is not supposed to be present on the source
                                // and that an upsert in ES does not remove fields
                                //context.assertFalse(finalResult.containsKey("my_flag"), "The final version of the document should not contain a flag but it was instead " + finalResult.getString("my_flag"));
                                final JsonArray subresources = finalResult.getJsonArray("subresources", new JsonArray());
                                final Set<String> srContents = subresources.stream().map(sr -> ((JsonObject) sr).getString("contentHtml")).collect(Collectors.toSet());
                                context.assertEquals(nbMessages, srContents.size(), "There should be exactly one sub resource per message. What we got back is " + srContents);
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
    private List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForRedisXAdd() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRules("my_flag", ".*fail.*", "redis-xadd");
        errors.addAll(evictionRulePG("content", ".*fail.*"));
        return errors;
    }
    private List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForRedisRead() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRules("my_flag", ".*fail.*", "redis-read");
        errors.addAll(evictionRulePG("content", ".*fail.*"));
        return errors;
    }

    private void clearErrorRules() {
        job.getMessageTransformer().clearChain();
    }


    private List<JsonObject> generateModifiedResourcesToFail(JsonObject originalResource, final int numberOfMessages) {
        return IntStream.range(0, numberOfMessages).mapToObj(i -> {
            final JsonObject modifiedResource = originalResource.copy();
            final int idxMessage = indexMessage.incrementAndGet();
            modifiedResource.put("content", "modified for failure number " + idxMessage);
            modifiedResource.put("my_flag", "fail " + idxMessage);
            modifiedResource.put("_id", originalResource.getString("assetId"));
            modifiedResource.put("version", indexMessage.get());
            final JsonArray subResources = originalResource.getJsonArray("subresources", new JsonArray());
            final String subResourceId = String.valueOf(indexMessage.incrementAndGet());
            final JsonObject subResource = new JsonObject().put("id", subResourceId);
            subResource.put("contentHtml", "<div>Sub resource " + subResourceId + " of failed resource " + idxMessage + " <div>");
            subResource.put("deleted", false);
            subResource.put("version", indexMessage.get());
            subResources.add(subResource);
            modifiedResource.put("subresources", subResources);
            return modifiedResource;
        }).collect(Collectors.toList());
    }

    private List<JsonObject> generateModifiedResourcesToSucceed(JsonObject originalResource, int numberOfMessages,
                                                                final String messagePrefix) {
        final String prefix = messagePrefix == null ? "modified for success number " : messagePrefix;
        return IntStream.range(0, numberOfMessages).mapToObj(i -> {
            final int idxMessage = indexMessage.incrementAndGet();
            final JsonObject modifiedResource = originalResource.copy();
            modifiedResource.put("content", prefix + indexMessage.incrementAndGet());
            modifiedResource.put("_id", originalResource.getString("assetId"));
            modifiedResource.put("version", indexMessage.get());
            final JsonArray subResources = originalResource.getJsonArray("subresources", new JsonArray());
            final String subResourceId = String.valueOf(indexMessage.incrementAndGet());
            final JsonObject subResource = new JsonObject().put("id", subResourceId);
            subResource.put("contentHtml", "<div>Sub resource " + subResourceId + " of succeeded resource " + idxMessage + " <div>");
            subResource.put("deleted", false);
            subResource.put("version", indexMessage.get());
            subResources.add(subResource);
            modifiedResource.put("subresources", subResources);
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
        return evictionRules(attributeName, attributeValue, pointOfFailure, null);
    }
    private List<ErrorMessageTransformer.IngestJobErrorRule> evictionRules(final String attributeName, final String attributeValue, final String pointOfFailure, final String trigger) {
        final List<ErrorMessageTransformer.IngestJobErrorRule> rules = new ArrayList<>();
        rules.add(new ErrorMessageTransformer.IngestJobErrorRuleBuilder()
                .withValueToTarget(attributeName, attributeValue)
                .setPointOfFailure(pointOfFailure)
                .setTriggeredAction(trigger)
                .createIngestJobErrorRule());
        return rules;
    }

    private static class TestConfiguration {
        final int nbFirstMessagesOk;
        final int nbMessagesKO;
        final int nbLastMessagesOk;
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors;

        public TestConfiguration(int nbFirstMessagesOk, int nbMessagesKO, int nbLastMessagesOk, List<ErrorMessageTransformer.IngestJobErrorRule> errors) {
            this.nbFirstMessagesOk = nbFirstMessagesOk;
            this.nbMessagesKO = nbMessagesKO;
            this.nbLastMessagesOk = nbLastMessagesOk;
            this.errors = errors;
        }
    }

    /* WIP
    @Test
    public void testNResourcesMErrors(TestContext context) {
        final List<TestConfiguration> configurations = IntStream.range(0, BATCH_SIZE)
            .mapToObj(resourceIndex -> new TestConfiguration(1 + resourceIndex, BATCH_SIZE * resourceIndex, 1 + resourceIndex,
                    resourceIndex % 2 == 0 ? createErrorRulesForES() : createErrorRulesForPG()))
            .collect(Collectors.toList());
        testInterspersedErrorMessages(configurations, context);
    }


    public void testInterspersedErrorMessages(final List<TestConfiguration> configurations, final TestContext context) {
        final Map<String, JsonObject> resources = configurations.stream().map(conf -> {
            final String resourceName = "resource" + idtResource.incrementAndGet();
            final JsonObject f1 = resource(resourceName);
            f1.put("content", "initial");
            return f1;
        }).collect(Collectors.toMap(o -> o.getString("name"), o -> o));
        final Set<String> resourceNames = resources.keySet();
        final UserInfos user = test.directory().generateUser("usermove");
        final Async async = context.async();
        final int nbTimesToExecuteJob = 5 * configurations.stream().mapToInt(c -> c.nbFirstMessagesOk + c.nbMessagesKO + c.nbLastMessagesOk).sum();
        resourceService.fetch(user, application, new ResourceSearchOperation()).onComplete(context.asyncAssertSuccess(fetch0 -> {
            context.assertTrue(
                    fetch0.stream().noneMatch(resource -> resourceNames.contains(((JsonObject)resource).getString("name", ""))),
                    "The user already has a resource called something like " + resourceNames
            );
            plugin.create(user, new ArrayList<>(resources.values()), false).onComplete(context.asyncAssertSuccess(r -> {
                executeJobNTimesAndFetchUniqueResults(resourceNames.size(), user, resourceNames, context).compose(createdResources -> {
                    ////////////////////////////
                    // Generate update messages
                    createdResources.stream().map(createdResource -> )
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

    private Future<JsonObject> executeJobNTimesAndFetchUniqueResults(final int nbBatchExecutions, final UserInfos user,
                                                                    final Set<String> resourceNames, final TestContext context) {
        return executeJobNTimes(nbBatchExecutions, context).flatMap(e ->
                resourceService.fetch(user, application, new ResourceSearchOperation())
                        .map(results -> {
                            final List<JsonObject> resultsForMyResource = results.stream().map(r -> ((JsonObject)r))
                                    .filter(r -> resourceNames.contains(r.getString("name", "")))
                                    .collect(Collectors.toList());
                            context.assertEquals(resourceNames.size(), resultsForMyResource.size());
                            return resultsForMyResource.get(0);
                        })
        );
    }
     */
}
