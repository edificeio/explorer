package com.opendigitaleducation.explorer.tests;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorderFactory;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.DefaultMuteService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.ExplorerPluginMetricsFactory;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.IExplorerPluginMetricsRecorder;
import org.entcore.common.explorer.impl.ExplorerPluginCommunicationPostgres;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExplorerTestHelper implements TestRule {
    static final Logger logger = LoggerFactory.getLogger(ExplorerTestHelper.class);
    private static final TestHelper testHelper = TestHelper.helper();
    public ElasticsearchContainer esContainer = testHelper.database().createOpenSearchContainer().withReuse(true);
    public PostgreSQLContainer<?> pgContainer = testHelper.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    private final String application;
    private IngestJob job;
    private String resourceIndex;
    private ResourceService resourceService;
    private ElasticClientManager elasticClientManager;
    private IExplorerPluginCommunication communication;
    private FolderService folderService;

    public ExplorerTestHelper(final String application) {
        this.application = application;
    }

    public TestHelper getTestHelper() {
        return testHelper;
    }

    public void init(){
        try {
            final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
            elasticClientManager = new ElasticClientManager(testHelper.vertx(), uris);
            resourceIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
            logger.info("Using index: " + resourceIndex);
            ExplorerPluginMetricsFactory.init(testHelper.vertx(), new JsonObject());
            IngestJobMetricsRecorderFactory.init(testHelper.vertx(), new JsonObject());
            ExplorerConfig.getInstance().setEsIndex(application, resourceIndex);
            final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
            final PostgresClient postgresClient = new PostgresClient(testHelper.vertx(), postgresqlConfig);
            final ShareTableManager shareTableManager = new DefaultShareTableManager();
            communication = new ExplorerPluginCommunicationPostgres(testHelper.vertx(), postgresClient, IExplorerPluginMetricsRecorder.NoopExplorerPluginMetricsRecorder.instance);
            final MuteService muteService = new DefaultMuteService(testHelper.vertx(), new ResourceExplorerDbSql(postgresClient));
            resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, communication, postgresClient, muteService);
            final MessageReader reader = MessageReader.postgres(postgresClient, new JsonObject());
            job = IngestJob.createForTest(testHelper.vertx(), elasticClientManager, postgresClient, new JsonObject(), reader);
            final JsonObject config = new JsonObject().put("stream", "postgres");
            final FolderExplorerPlugin folderPlugin = FolderExplorerPlugin.create(testHelper.vertx(), config, postgresClient);
            folderService = new FolderServiceElastic(elasticClientManager, folderPlugin);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    static  Future createUpsertScript(final TestContext context, final String scriptId, final ElasticClientManager elasticClientManager) {
        final Buffer upsertScript = testHelper.vertx().fileSystem().readFileBlocking("es/upsertScript.json");
        final Future future = elasticClientManager.getClient().storeScript(scriptId, upsertScript);
        return future;
    }

    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = testHelper.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    public IExplorerPluginCommunication getCommunication() {
        return communication;
    }

    public IngestJob getJob() {
        return job;
    }

    public void start(final TestContext context){
        final Async async = context.async();
        createMapping(elasticClientManager, context, resourceIndex).compose(e -> {
            return createUpsertScript(context, "explorer-upsert-ressource", elasticClientManager);
        }).onComplete(r -> async.complete());
    }

    public void close() {
        esContainer.close();
        pgContainer.close();
    }

    public Future<JsonArray> fetch(final UserInfos user, final String application, final ResourceSearchOperation operation){
        return resourceService.fetch(user, application, operation);
    }

    public Future<JsonArray> fetchFolders(final UserInfos user, final String application, final Optional<String> parentId){
        return folderService.fetch(user, application, parentId);
    }

    public Future<Void> ingestJobExecute(boolean force) {
        return job.execute(true);
    }

    public Future<Void> ingestJobWaitPending() {
        return job.waitPending();
    }

    public ResourceSearchOperation createSearch(){
        return new ResourceSearchOperation();
    }

    public Future<Void> initFolderMapping(){
        return this.resourceService.initMapping(ExplorerConfig.FOLDER_APPLICATION);
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return esContainer.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                pgContainer.apply(new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        ExplorerTestHelper.this.init();
                        statement.evaluate();
                    }
                }, description).evaluate();
            }
        },description);
    }

    public static Future<JsonObject> executeJobAndFetchUniqueResult(final IngestJob job,
                                                                    final String application,
                                                                    final ResourceService resourceService,
                                                                    final UserInfos user,
                                                                    final String resourceName,
                                                                    final TestContext context) {
        return executeJobNTimesAndFetchUniqueResult(job, 1, application, resourceService, user, resourceName, context);
    }

    public static Future<JsonObject> executeJobNTimesAndFetchUniqueResult(final IngestJob job,
                                                                          final int nbBatchExecutions,
                                                                          final String application,
                                                                          final ResourceService resourceService,
                                                                          final UserInfos user,
                                                                          final String resourceName, final TestContext context) {
        return executeJobNTimes(job, nbBatchExecutions, context).flatMap(e ->
                        resourceService.fetch(user, application, new ResourceSearchOperation())
                                .map(results -> {
                                    final List<JsonObject> resultsForMyResource = results.stream().map(r -> ((JsonObject)r))
                                            .filter(r -> r.getString("name", "").equals(resourceName))
                                            .collect(Collectors.toList());
                                    context.assertEquals(1, resultsForMyResource.size(), "Could not find the resource that was inserted");
                                    return resultsForMyResource.get(0);
                                })
                                .onFailure(Throwable::printStackTrace)
                )
                .onFailure(Throwable::printStackTrace);
    }


    public static Future<Object> executeJobNTimes(final IngestJob job, int nbTimesToExecute, final TestContext context) {
        final Future<Object> onDone;
        if (nbTimesToExecute <= 0) {
            onDone = Future.succeededFuture();
        } else {
            System.out.println("Still " + nbTimesToExecute + " times to execute the job");
            onDone = job.execute(true).compose(e -> executeJobNTimes(job, nbTimesToExecute - 1, context));
        }
        return onDone.onFailure(e -> context.asyncAssertFailure());
    }

    public static Future<Void> createScript(final Vertx vertx, ElasticClientManager elasticClientManager) {
        final Buffer upsertScript = vertx.fileSystem().readFileBlocking("es/upsertScript.json");
        return elasticClientManager.getClient().storeScript("explorer-upsert-ressource", upsertScript);
    }


    public static Future<Void> createMapping(final Vertx vertx, ElasticClientManager elasticClientManager, String index) {
        final Buffer mapping = vertx.fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }
}
