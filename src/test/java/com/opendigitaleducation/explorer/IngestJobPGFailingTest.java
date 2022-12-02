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
import io.vertx.core.json.JsonArray;
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
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

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
public class IngestJobPGFailingTest {
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
        ExplorerConfig.getInstance().setEsIndex(FakeMongoPlugin.FAKE_APPLICATION, resourceIndex);
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final JsonObject mongoConfig = new JsonObject().put("connection_string", mongoDBContainer.getReplicaSetUrl());
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        redisClient = new FaillibleRedisClient(test.vertx(), redisConfig);
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
    }


    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    static JsonObject resource(final String name) {
        return new JsonObject().put("name", name);
    }

    /**
     * <u>Goal : </u> Call ES many times to make it crash and expect the job to yield a result everytime.
     * @param context Test context
     */
    @Test
    public void stress(TestContext context) {
        final int nbFirstMessagesOk = 1;
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = createErrorRulesForES();
        final String resourceName = "resource" + idtResource.incrementAndGet();
        final JsonObject f1 = resource(resourceName);
        f1.put("content", "initial");
        final UserInfos user = test.directory().generateUser("usermove");
        final Async async = context.async();
        final int nbTimesToExecuteJob = 10;
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
                    setErrorRules(errors);
                    pgContainer.stop();
                    return pluginNotifyUpsert(user, modifications).onComplete(context.asyncAssertSuccess(r2 -> {
                        executeJobNTimesAndFetchUniqueResult(nbTimesToExecuteJob, user, resourceName, context).onComplete(context.asyncAssertSuccess(asReturnedByFetch -> {
                            async.complete();
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
            .onFailure(Throwable::printStackTrace)
        )
        .onFailure(Throwable::printStackTrace);
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
    private List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForRedis() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRules("my_flag", ".*fail.*", "xAdd");
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
            System.out.println("Still " + nbTimesToExecute + " times to execute the job");
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
            final JsonArray subResources = originalResource.getJsonArray("subresources", new JsonArray());
            final String subResourceId = String.valueOf(indexMessage.incrementAndGet());
            final JsonObject subResource = new JsonObject().put("id", subResourceId);
            subResource.put("contentHtml", "<div>Sub resource " + subResourceId + " of failed resource " + idxMessage + " <div>");
            subResource.put("deleted", false);
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
            final JsonArray subResources = originalResource.getJsonArray("subresources", new JsonArray());
            final String subResourceId = String.valueOf(indexMessage.incrementAndGet());
            final JsonObject subResource = new JsonObject().put("id", subResourceId);
            subResource.put("contentHtml", "<div>Sub resource " + subResourceId + " of succeeded resource " + idxMessage + " <div>");
            subResource.put("deleted", false);
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
        final List<ErrorMessageTransformer.IngestJobErrorRule> rules = new ArrayList<>();
        rules.add(new ErrorMessageTransformer.IngestJobErrorRuleBuilder()
                .withValueToTarget(attributeName, attributeValue)
                .setPointOfFailure(pointOfFailure)
                .createIngestJobErrorRule());
        return rules;
    }
}
