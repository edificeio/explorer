package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.jobs.PostgresResourceLoader;
import com.opendigitaleducation.explorer.jobs.ResourceLoader;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.services.ExplorerService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ElasticResourceService;
import com.opendigitaleducation.explorer.services.impl.PostgresExplorerService;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;

@RunWith(VertxUnitRunner.class)
public class ResourceServiceTest {
    private static final TestHelper test = TestHelper.helper();

    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.9.0").withReuse(true);
    @ClassRule
    public static PostgreSQLContainer<?> pgContainer = test.database().createPostgreSQLContainer().withInitScript("initExplorer.sql").withReuse(true);
    static ElasticClientManager elasticClientManager;
    static PostgresClient postgresClient;
    static ResourceService resourceService;
    static ExplorerService explorerService;
    static ResourceLoader resourceLoader;

    //TODO add more logs
    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final JsonObject postgresql = new JsonObject().put("host", pgContainer.getHost()).put("database", pgContainer.getDatabaseName()).put("user", pgContainer.getUsername()).put("password", pgContainer.getPassword()).put("port", pgContainer.getMappedPort(5432));
        final HttpClientOptions httpOptions = new HttpClientOptions().setDefaultHost(esContainer.getHost()).setDefaultPort(esContainer.getMappedPort(9200));
        final HttpClient httpClient = test.vertx().createHttpClient(httpOptions);
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        postgresClient = new PostgresClient(test.vertx(), postgresql);
        final String index = ResourceService.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
        System.out.println("Using index: " + index);
        resourceService = new ElasticResourceService(elasticClientManager, index);
        explorerService = new PostgresExplorerService(postgresClient);
        resourceLoader = new PostgresResourceLoader(test.vertx(), postgresClient, resourceService, new JsonObject());
        final Async async = context.async();
        createMapping(context, index).setHandler(r -> async.complete());
    }

    static Future<Void> createMapping(TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    @Test
    public void testShouldIntegrateNewResource(TestContext context) {
        final UserInfos user = test.http().sessionUser();
        final ExplorerService.ExplorerMessageBuilder message = ExplorerService.ExplorerMessageBuilder.create("id1", user);
        message.withContent("my text....").withName("name1").withPublic(true).withResourceType("blog", "post");
        resourceLoader.start();
        final Future<ResourceLoader.ResourceLoaderResult> future = Future.future();
        resourceLoader.setOnEnd(future.completer());
        explorerService.push(message).setHandler(context.asyncAssertSuccess(push -> {
            future.setHandler(context.asyncAssertSuccess(results -> {
                context.assertEquals(1, results.getSucceed().size());
            }));
        }));
    }

    @Test
    public void testShouldIntegrateResourceOnRestart(TestContext context) {

    }

    @Test
    public void testShouldExploreResource(TestContext context) {

    }

    @Test
    public void testShouldSearchResource(TestContext context) {

    }

}
