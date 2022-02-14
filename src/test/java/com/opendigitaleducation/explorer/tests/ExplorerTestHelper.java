package com.opendigitaleducation.explorer.tests;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.FakeMongoPlugin;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import org.entcore.common.elasticsearch.ElasticClientManager;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.explorer.ExplorerPluginCommunicationPostgres;
import org.entcore.common.postgres.PostgresClient;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;

public class ExplorerTestHelper implements TestRule {
    static final Logger logger = LoggerFactory.getLogger(ExplorerTestHelper.class);
    private static final TestHelper test = TestHelper.helper();
    public ElasticsearchContainer esContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.9.3").withReuse(true);
    public PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    private final String application;
    private IngestJob job;
    private String resourceIndex;
    private ResourceService resourceService;
    private ElasticClientManager elasticClientManager;
    private IExplorerPluginCommunication communication;

    public ExplorerTestHelper(final String application) {
        this.application = application;
    }
    public void init(){
        try {
            final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
            elasticClientManager = new ElasticClientManager(test.vertx(), uris);
            resourceIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
            logger.info("Using index: " + resourceIndex);
            ExplorerConfig.getInstance().setEsIndex(application, resourceIndex);
            final JsonObject postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
            final PostgresClient postgresClient = new PostgresClient(test.vertx(), postgresqlConfig);
            final ShareTableManager shareTableManager = new DefaultShareTableManager();
            communication = new ExplorerPluginCommunicationPostgres(test.vertx(), postgresClient);
            resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, communication, postgresClient);
            final MessageReader reader = MessageReader.postgres(postgresClient, new JsonObject());
            job = IngestJob.create(test.vertx(), elasticClientManager, postgresClient, new JsonObject(), reader);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    static Future<Void> createMapping(ElasticClientManager elasticClientManager, TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
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
        createMapping(elasticClientManager, context, resourceIndex).onComplete(r -> async.complete());
    }

    public void close() {
        esContainer.close();
        pgContainer.close();
    }

    public Future<JsonArray> fetch(final UserInfos user, final String application, final ResourceService.SearchOperation operation){
        return resourceService.fetch(user, application, new ResourceService.SearchOperation());
    }

    public Future<Void> ingestJobExecute(boolean force) {
        return job.execute(true);
    }

    public Future<Void> ingestJobWaitPending() {
        return job.waitPending();
    }

    public ResourceService.SearchOperation createSearch(){
        return new ResourceService.SearchOperation();
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
}
