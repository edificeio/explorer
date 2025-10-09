package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.controllers.ExplorerController;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.ingest.impl.ErrorMessageTransformer;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.DefaultMuteService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import com.opendigitaleducation.explorer.tests.ExplorerTestHelper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.explorer.ExplorerPluginMetricsFactory;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.IExplorerPluginMetricsRecorder;
import org.entcore.common.explorer.impl.ExplorerPluginClient;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.mute.MuteHelper;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.share.ShareRoles;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.opendigitaleducation.explorer.tests.ExplorerTestHelper.createScript;
import static io.vertx.core.CompositeFuture.all;

public class FullExplorerStackTest {
    protected static final int BATCH_SIZE = 5;
    protected static final TestHelper test = TestHelper.helper();
    @ClassRule
    public static Neo4jContainer<?> neo4jContainer = test.database().createNeo4jContainer();
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
    static FolderService folderService;
    static FakeMongoPlugin plugin;
    static FaillibleRedisClient redisClient;
    static String application;
    static IngestJob job;
    static MongoClient mongoClient;
    static ExplorerPluginClient pluginClient;
    static AtomicInteger idtResource = new AtomicInteger(0);
    static AtomicInteger indexMessage = new AtomicInteger(0);
    static ExplorerController controller;
    static MuteHelper muteHelper;
    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        test.database().initNeo4j(context, neo4jContainer);
        test.database().initMongo(context, mongoDBContainer);
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final String resourceIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + System.currentTimeMillis();
        final String folderIndex = ExplorerConfig.DEFAULT_FOLDER_INDEX + "_" + System.currentTimeMillis();
        System.out.println("Using index: " + resourceIndex);
        IngestJobMetricsRecorderFactory.init(null, new JsonObject());
        ExplorerPluginMetricsFactory.init(test.vertx(), new JsonObject());
        ExplorerConfig.getInstance().setEsIndex(ExplorerConfig.FOLDER_APPLICATION, folderIndex);
        ExplorerConfig.getInstance().setEsIndex(FakeMongoPlugin.FAKE_APPLICATION, resourceIndex);
        EventStoreFactory.getFactory().setVertx(test.vertx());
        final JsonObject redisConfig = new JsonObject().put("host", redisContainer.getHost()).put("port", redisContainer.getMappedPort(6379));
        final JsonObject mongoConfig = new JsonObject().put("connection_string", mongoDBContainer.getReplicaSetUrl());
        final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
        redisClient = new FaillibleRedisClient(test.vertx(), redisConfig);
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        IExplorerPluginCommunication communication = new ExplorerPluginCommunicationPostgres(test.vertx(), postgresClient, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
        mongoClient = MongoClient.createShared(test.vertx(), mongoConfig);
        final FolderExplorerPlugin folderPlugin = FolderExplorerPlugin.withRedisStream(test.vertx(), redisClient, postgresClient);
        folderPlugin.start();
        final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
        muteHelper = new MuteHelper(test.vertx());
        resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, communication, postgresClient, muteService);
        final FolderService folderService = new FolderServiceElastic(elasticClientManager, folderPlugin, resourceService);
        plugin = FakeMongoPlugin.withRedisStream(test.vertx(), redisClient, mongoClient);
        plugin.start();
        application = plugin.getApplication();
        controller = new ExplorerController(folderService, resourceService);
        controller.init(test.vertx(), new JsonObject(), null, null);
        final Async async = context.async();
        final Promise<Void> promiseMongo = Promise.promise();
        final Promise<Void> promiseRedis = Promise.promise();
        final Promise<Void> promiseScript = Promise.promise();
        all(promiseRedis.future(), promiseRedis.future(), promiseScript.future()).onComplete(e -> async.complete());
        ExplorerTestHelper.createMapping(test.vertx(), elasticClientManager, resourceIndex).onComplete(r -> promiseMongo.complete());
        createScript(test.vertx(), elasticClientManager).onComplete(r -> promiseScript.complete());
        test.http().mockJsonValidator();
        final JsonObject jobConf = new JsonObject()
                .put("error-rules-allowed", true)
                .put("batch-size", BATCH_SIZE)
                .put("max-delay-ms", 2000)
                .put("message-merger", "noop")
                .put("opensearch-options", new JsonObject().put("wait-for", true));
        pluginClient = IExplorerPluginClient.withBus(test.vertx(), FakeMongoPlugin.FAKE_APPLICATION, FakeMongoPlugin.FAKE_TYPE);
        final JsonObject rights = new JsonObject();
        //flush redis
        redisClient.getClient().flushall(new ArrayList<>(), e -> {
            final MessageReader reader = MessageReader.redis(test.vertx(), redisClient, redisConfig);
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

    protected void clearErrorRules() {
        job.getMessageTransformer().clearChain();
    }

    public static List<ErrorMessageTransformer.IngestJobErrorRule> evictionRuleES(final String attributeName, final String attributeValue) {
        return evictionRules(attributeName, attributeValue, "es");
    }

    public static List<ErrorMessageTransformer.IngestJobErrorRule> evictionRulePG(final String attributeName, final String attributeValue) {
        return evictionRules(attributeName, attributeValue, "pg-ingest");
    }

    public static List<ErrorMessageTransformer.IngestJobErrorRule> evictionRules(final String attributeName, final String attributeValue, final String pointOfFailure) {
        return evictionRules(attributeName, attributeValue, pointOfFailure, null);
    }
    public static List<ErrorMessageTransformer.IngestJobErrorRule> evictionRules(final String attributeName, final String attributeValue, final String pointOfFailure, final String trigger) {
        final List<ErrorMessageTransformer.IngestJobErrorRule> rules = new ArrayList<>();
        rules.add(new ErrorMessageTransformer.IngestJobErrorRuleBuilder()
                .withValueToTarget(attributeName, attributeValue)
                .setPointOfFailure(pointOfFailure)
                .setTriggeredAction(trigger)
                .createIngestJobErrorRule());
        return rules;
    }

    public static List<JsonObject> generateModifiedResourcesToFail(JsonObject originalResource, final int numberOfMessages) {
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

    public static List<JsonObject> generateModifiedResourcesToSucceed(JsonObject originalResource, int numberOfMessages,
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


    public static List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForES() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRuleES("my_flag", ".*fail.*");
        errors.addAll(evictionRuleES("content", ".*fail.*"));
        return errors;
    }
    public static List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForPG() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRulePG("my_flag", ".*fail.*");
        errors.addAll(evictionRulePG("content", ".*fail.*"));
        return errors;
    }
    public static List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForRedisXAdd() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRules("my_flag", ".*fail.*", "redis-xadd");
        errors.addAll(evictionRulePG("content", ".*fail.*"));
        return errors;
    }
    public static List<ErrorMessageTransformer.IngestJobErrorRule> createErrorRulesForRedisRead() {
        final List<ErrorMessageTransformer.IngestJobErrorRule> errors = evictionRules("my_flag", ".*fail.*", "redis-read");
        errors.addAll(evictionRulePG("content", ".*fail.*"));
        return errors;
    }

    protected void setErrorRules(List<ErrorMessageTransformer.IngestJobErrorRule> errors) {
        job.getMessageTransformer()
                .clearChain()
                .withTransformer(new ErrorMessageTransformer(errors));
    }

    protected Future<Void> pluginNotifyUpsert(UserInfos user, List<JsonObject> modifications) {
        return pluginNotifyUpsert(user, modifications, 0);
    }
    protected Future<Void> pluginNotifyUpsert(UserInfos user, List<JsonObject> modifications, final int position) {
        final Future<Void> done;
        if(modifications == null || modifications.isEmpty() || position >= modifications.size()) {
            done = Future.succeededFuture();
        } else {
            done = plugin.notifyUpsert(user, modifications.get(position))
                    .compose(e -> pluginNotifyUpsert(user, modifications, position + 1));
        }
        return done;
    }

    public static JsonObject resource(final String name) {
        return new JsonObject()
                .put("name", name)
                .put("version", 1);
    }
}
