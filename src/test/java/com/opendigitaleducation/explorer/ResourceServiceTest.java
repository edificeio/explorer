package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.jobs.PostgresResourceLoader;
import com.opendigitaleducation.explorer.jobs.ResourceLoader;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.services.ExplorerService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ElasticResourceService;
import com.opendigitaleducation.explorer.services.impl.PostgresExplorerService;
import com.opendigitaleducation.explorer.share.PostgresShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
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
import java.util.Arrays;
import java.util.Optional;

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
        final ShareTableManager shareTableManager = new PostgresShareTableManager(postgresClient);
        resourceService = new ElasticResourceService(elasticClientManager, shareTableManager, index);
        explorerService = new PostgresExplorerService(test.vertx(), postgresClient);
        resourceLoader = new PostgresResourceLoader(test.vertx(), postgresClient, resourceService, new JsonObject());
        final Async async = context.async();
        createMapping(context, index).setHandler(r -> async.complete());
    }

    @Before
    public void before(){
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
        final UserInfos user2 = test.directory().generateUser("user2");
        final UserInfos user = test.http().sessionUser();
        final ExplorerService.ExplorerMessageBuilder message1 = create(user, "idexplore1", "name1", "text1");
        final ExplorerService.ExplorerMessageBuilder message2 = create(user, "idexplore2", "name2", "text2");
        final ExplorerService.ExplorerMessageBuilder message3 = create(user2, "idexplore3", "name3", "text3");
        final ExplorerService.ExplorerMessageBuilder message2_1 = create(user, "idexplore2_1", "name2_1", "text2_1");
        explorerService.push(Arrays.asList(message1, message2, message3, message2_1)).setHandler(context.asyncAssertSuccess(push->{
            resourceLoader.execute(true).setHandler(context.asyncAssertSuccess(load->{
                resourceService.fetch(user, "blog", new ResourceService.SearchOperation()).setHandler(context.asyncAssertSuccess(fetch1->{
                    context.assertEquals(3, fetch1.size());
                    final JsonObject json = fetch1.stream().map(e-> (JsonObject)e).filter(e-> e.getString("_id").equals("idexplore2_1")).findFirst().get();
                    resourceService.move(user, json, Optional.empty(), Optional.of("folder1")).setHandler(context.asyncAssertSuccess(move->{
                        resourceService.fetch(user, "blog", new ResourceService.SearchOperation().setParentId(Optional.of("folder1"))).setHandler(context.asyncAssertSuccess(fetch2->{
                            context.assertEquals(1, fetch2.size());
                        }));
                        resourceService.fetch(user, "blog", new ResourceService.SearchOperation().setParentId(Optional.empty())).setHandler(context.asyncAssertSuccess(fetch2->{
                            context.assertEquals(2, fetch2.size());
                        }));
                    }));
                }));
                resourceService.fetch(user, "blog", new ResourceService.SearchOperation().setSearch("text2")).setHandler(context.asyncAssertSuccess(fetch1->{
                    context.assertEquals(1, fetch1.size());
                }));
            }));
        }));
    }

    @Test
    public void testShouldExploreResourceByShare(TestContext context) {

    }

    @Test
    public void testShouldSearchResource(TestContext context) {

    }

}
