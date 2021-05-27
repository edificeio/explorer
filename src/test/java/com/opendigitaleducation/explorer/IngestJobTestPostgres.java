package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.plugin.ExplorerPlugin;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunication;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;

@RunWith(VertxUnitRunner.class)
public class IngestJobTestPostgres extends IngestJobTest {
    private IngestJob job;
    private PostgresClient postgresClient;
    private ExplorerPlugin explorerPlugin;
    private ShareTableManager shareTableManager;
    private ResourceService resourceService;
    private JsonObject postgresqlConfig;
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);


    protected JsonObject getPostgresConfig(){
        if(postgresqlConfig == null){
            postgresqlConfig = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        }
        return postgresqlConfig;
    }

    protected PostgresClient getPostgresClient(){
        if(postgresClient == null) {
            postgresClient = new PostgresClient(test.vertx(), getPostgresConfig());
        }
        return postgresClient;
    }

    @Override
    protected IngestJob getIngestJob() {
        if (job == null) {
            final MessageReader reader = MessageReader.postgres(getPostgresClient(), new JsonObject());
            job = IngestJob.create(test.vertx(), elasticClientManager,getPostgresClient(), new JsonObject(), reader);
        }
        return job;
    }

    @Override
    protected ExplorerPlugin getExplorerPlugin() {
        if(explorerPlugin == null){
            explorerPlugin = FakePostgresPlugin.withPostgresChannel(test.vertx(), getPostgresClient());
        }
        return explorerPlugin;
    }

    @Override
    public ResourceService getResourceService() {
        if(resourceService == null){
            final ExplorerPluginCommunication comm = getExplorerPlugin().getCommunication();
            resourceService = new ResourceServiceElastic(elasticClientManager, getShareTableManager(), comm, getPostgresClient());
        }
        return resourceService;
    }

    @Override
    public ShareTableManager getShareTableManager() {
        if(shareTableManager == null){
            shareTableManager = new DefaultShareTableManager();
        }
        return shareTableManager;
    }
}
