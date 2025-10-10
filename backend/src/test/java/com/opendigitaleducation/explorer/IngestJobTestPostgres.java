package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.services.MuteService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.DefaultMuteService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.explorer.impl.ExplorerPlugin;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.postgres.PostgresClient;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;

@RunWith(VertxUnitRunner.class)
public class IngestJobTestPostgres extends IngestJobTest {
  // TODO JBER and MEST - reactivate tests
  /*
    private IngestJob job;
    private IPostgresClient postgresClient;
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

    protected Future<IPostgresClient> getPostgresClient() {
        if(postgresClient == null) {
            try {
                final JsonObject json = new JsonObject().put("postgresConfig", getPostgresConfig());
                //IPostgresClient.initPostgresConsumer(test.vertx(), json, true);
                return IPostgresClient.create(test.vertx(), json, true, false)
                  .onSuccess(postgresClient -> this.postgresClient = postgresClient);
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        }
        return Future.succeededFuture(postgresClient);
    }

    @Override
    protected Future<IngestJob> getIngestJob() {
      return Future.<IngestJob>future(p -> {
        if (job == null) {
          getPostgresClient().onSuccess(client -> {
            final MessageReader reader = MessageReader.postgres(client, new JsonObject());
            final JsonObject jobConfig = new JsonObject().put("opensearch-options", new JsonObject().put("wait-for", true));
            job = IngestJob.createForTest(test.vertx(), elasticClientManager, client, jobConfig, reader);
          }).onFailure(p::fail);
        } else {
          p.complete(job);
        }
      });
    }

    @Override
    protected Future<ExplorerPlugin> getExplorerPlugin() {
        if(explorerPlugin == null){
          return getPostgresClient()
            .map(pgClient -> {
              this.explorerPlugin = FakePostgresPlugin.withPostgresChannel(test.vertx(), pgClient);
              return this.explorerPlugin;
            });
        }
        return Future.succeededFuture(explorerPlugin);
    }

    @Override
    public ResourceService getResourceService() {
        if(resourceService == null){
            final IExplorerPluginCommunication comm = getExplorerPlugin().getCommunication();
            final MuteService muteService = new DefaultMuteService(test.vertx(), new ResourceExplorerDbSql(postgresClient));
            resourceService = new ResourceServiceElastic(elasticClientManager, getShareTableManager(), comm, getPostgresClient(), muteService);
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

   */
}
