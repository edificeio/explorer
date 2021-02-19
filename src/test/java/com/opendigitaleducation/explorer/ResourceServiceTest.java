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
import org.junit.*;
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
        explorerService = new PostgresExplorerService(test.vertx(), postgresClient);
        resourceLoader = new PostgresResourceLoader(test.vertx(), postgresClient, resourceService, new JsonObject());
        final Async async = context.async();
        createMapping(context, index).setHandler(r -> async.complete());
    }

    @Before
    public void after(){
        resourceLoader.stop();
    }

    static Future<Void> createMapping(TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    static ExplorerService.ExplorerMessageBuilder create(UserInfos user, String id, String name, String content) {
        return create(user, id, name, content, "blog", "post", false);
    }

    static ExplorerService.ExplorerMessageBuilder create(UserInfos user, String id, String name, String content, String application, String type) {
        return create(user, id, name, content, application, type, false);
    }

    static ExplorerService.ExplorerMessageBuilder create(UserInfos user, String id, String name, String content, String application, String type, boolean pub) {
        final ExplorerService.ExplorerMessageBuilder message = ExplorerService.ExplorerMessageBuilder.create(id, user);
        message.withContent(content).withName(name).withPublic(pub).withResourceType(application, type);
        return message;
    }

    static ExplorerService.ExplorerMessageBuilder update(UserInfos user, String id, String name, String content) {
        return update(user, id, name, content, "blog", "post", false);
    }

    static ExplorerService.ExplorerMessageBuilder update(UserInfos user, String id, String name, String content, String application, String type) {
        return update(user, id, name, content, application, type, false);
    }

    static ExplorerService.ExplorerMessageBuilder update(UserInfos user, String id, String name, String content, String application, String type, boolean pub) {
        final ExplorerService.ExplorerMessageBuilder message = ExplorerService.ExplorerMessageBuilder.update(id, user);
        message.withContent(content).withName(name).withPublic(pub).withResourceType(application, type);
        return message;
    }

    static ExplorerService.ExplorerMessageBuilder delete(UserInfos user, String id) {
        final ExplorerService.ExplorerMessageBuilder message = ExplorerService.ExplorerMessageBuilder.delete(id, user);
        return message;
    }

    @Test
    public void testShouldIntegrateNewResource(TestContext context) {
        final UserInfos user = test.http().sessionUser();
        resourceLoader.start().setHandler(context.asyncAssertSuccess(r -> {
            final Future<ResourceLoader.ResourceLoaderResult> fCreate = Future.future();
            resourceLoader.setOnEnd(fCreate.completer());
            final ExplorerService.ExplorerMessageBuilder message1 = create(user, "id1", "name1", "text1");
            explorerService.push(message1).setHandler(context.asyncAssertSuccess(push -> {
                fCreate.setHandler(context.asyncAssertSuccess(results -> {
                    context.assertEquals(1, results.getSucceed().size());
                    //update
                    final Future<ResourceLoader.ResourceLoaderResult> fUpdate = Future.future();
                    resourceLoader.setOnEnd(fUpdate.completer());
                    final ExplorerService.ExplorerMessageBuilder message2 = update(user, "id1", "name1_1", "text1_1");
                    explorerService.push(message2).setHandler(context.asyncAssertSuccess(push2 -> {
                        fUpdate.setHandler(context.asyncAssertSuccess(results2 -> {
                            context.assertEquals(1, results2.getSucceed().size());
                            //delete
                            final Future<ResourceLoader.ResourceLoaderResult> fDelete = Future.future();
                            resourceLoader.setOnEnd(fDelete.completer());
                            final ExplorerService.ExplorerMessageBuilder message3 = delete(user, "id1");
                            explorerService.push(message3).setHandler(context.asyncAssertSuccess(push3 -> {
                                fDelete.setHandler(context.asyncAssertSuccess(results3 -> {
                                    context.assertEquals(1, results3.getSucceed().size());
                                }));
                            }));
                        }));
                    }));
                }));
            }));
        }));
    }

    @Test
    public void testShouldIntegrateResourceOnRestart(TestContext context) {
        resourceLoader.stop();
        final UserInfos user = test.http().sessionUser();
        final ExplorerService.ExplorerMessageBuilder message1 = create(user, "id_restart1", "name1", "text1");
        explorerService.push(message1).setHandler(context.asyncAssertSuccess(push -> {
            final Future<ResourceLoader.ResourceLoaderResult> fCreate = Future.future();
            resourceLoader.setOnEnd(fCreate.completer());
            resourceLoader.start().setHandler(context.asyncAssertSuccess(r -> {
                fCreate.setHandler(context.asyncAssertSuccess(results -> {
                    context.assertEquals(1, results.getSucceed().size());
                }));
            }));
        }));
    }


    //TODO share
    //TODO redis try
    //TODO resource right retro

    @Test
    public void testShouldExploreResource(TestContext context) {

    }
    @Test
    public void testShouldExploreResourceByRights(TestContext context) {

    }

    @Test
    public void testShouldSearchResource(TestContext context) {

    }

}
