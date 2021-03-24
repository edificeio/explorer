package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.services.ExplorerService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.services.impl.ExplorerServicePostgres;
import com.opendigitaleducation.explorer.share.PostgresShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public abstract class ResourceServiceTest {
    protected static final TestHelper test = TestHelper.helper();
    protected static String esIndex;
    @ClassRule
    public static ElasticsearchContainer esContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.9.0").withReuse(true);
    static ElasticClientManager elasticClientManager;
    //TODO test failed case (ingest failed, ingest too many error, ingest too big payload, message read failed, message update status failed...)
    //TODO test metrics
    //TODO add more logs
    //TODO redis try
    //TODO resource right retro
    protected abstract IngestJob getIngestJob();
    protected abstract ExplorerService getExplorerService();
    protected abstract ShareTableManager getShareTableManager();
    protected abstract ResourceService getResourceService();
    
    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final HttpClientOptions httpOptions = new HttpClientOptions().setDefaultHost(esContainer.getHost()).setDefaultPort(esContainer.getMappedPort(9200));
        final HttpClient httpClient = test.vertx().createHttpClient(httpOptions);
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final Async async = context.async();
        esIndex = ResourceService.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
        System.out.println("Using index: " + esIndex);
        createMapping(context, esIndex).onComplete(r -> async.complete());
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
        final ExplorerService.ExplorerMessageBuilder message = ExplorerService.ExplorerMessageBuilder.delete(id, user).withResourceType("blog", "post");
        return message;
    }

    static List<ResourceService.ShareOperation> shareTo(JsonObject rights, UserInfos... users) {
        final List<ResourceService.ShareOperation> share = new ArrayList<>();
        for (UserInfos user : users) {
            final ResourceService.ShareOperation op = new ResourceService.ShareOperation(user.getUserId(), false, rights);
            share.add(op);
        }
        return share;
    }

    static List<ResourceService.ShareOperation> shareToGroup(JsonObject rights, String... groups) {
        final List<ResourceService.ShareOperation> share = new ArrayList<>();
        for (String group : groups) {
            final ResourceService.ShareOperation op = new ResourceService.ShareOperation(group, true, rights);
            share.add(op);
        }
        return share;
    }

    @Before
    public void before(TestContext context) {
        final Async async = context.async();
        getIngestJob().stop().onComplete(e->{
            getIngestJob().waitPending().onComplete(ee->{
                async.complete();
            });
        });
    }

    @After
    public void after(TestContext context) {
        final Async async = context.async();
        getIngestJob().stop().onComplete(e->{
            getIngestJob().waitPending().onComplete(ee->{
                async.complete();
            });
        });
    }
    //TODO redis test + folder (ingest) + share (without hash) + complex search
    //TODO http layer
    @Test
    public void testShouldIntegrateNewResource(TestContext context) {
        final IngestJob job = getIngestJob();
        final ExplorerService exService = getExplorerService();
        final UserInfos user = test.directory().generateUser("intergrate_res");
        job.start().onComplete(context.asyncAssertSuccess(r -> {
                final Promise<IngestJob.IngestJobResult> fCreate = Promise.promise();
                job.onEachExecutionEnd(fCreate);
                final ExplorerService.ExplorerMessageBuilder message1 = create(user, "id1", "name1", "text1");
                exService.push(message1).onComplete(context.asyncAssertSuccess(push -> {
                    fCreate.future().onComplete(context.asyncAssertSuccess(results -> {
                        context.assertEquals(1, results.getSucceed().size());
                        //update
                        final Promise<IngestJob.IngestJobResult> fUpdate = Promise.promise();
                        job.onEachExecutionEnd(fUpdate);
                        final ExplorerService.ExplorerMessageBuilder message2 = update(user, "id1", "name1_1", "text1_1");
                        exService.push(message2).onComplete(context.asyncAssertSuccess(push2 -> {
                            fUpdate.future().onComplete(context.asyncAssertSuccess(results2 -> {
                                context.assertEquals(1, results2.getSucceed().size());
                                getResourceService().fetch(user, "blog", new ResourceService.SearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                                    context.assertEquals(1, fetch1.size());
                                    //delete
                                    final Promise<IngestJob.IngestJobResult> fDelete = Promise.promise();
                                    job.onEachExecutionEnd(fDelete);
                                    final ExplorerService.ExplorerMessageBuilder message3 = delete(user, "id1");
                                    exService.push(message3).onComplete(context.asyncAssertSuccess(push3 -> {
                                        fDelete.future().onComplete(context.asyncAssertSuccess(results3 -> {
                                            context.assertEquals(1, results3.getSucceed().size());
                                            getResourceService().fetch(user, "blog", new ResourceService.SearchOperation()).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                                context.assertEquals(0, fetch2.size());
                                            }));
                                        }));
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
        getExplorerService().push(message1).compose((push -> {
            final Promise<IngestJob.IngestJobResult> fCreate = Promise.promise();
            getIngestJob().onEachExecutionEnd(fCreate);
            getIngestJob().start();
            return fCreate.future();
        })).onComplete(context.asyncAssertSuccess(results -> {
            context.assertEquals(0, results.getFailed().size());
            //if result empty => maybe notification start at same time of first execution
            if(results.getSucceed().isEmpty()){
                final Promise<IngestJob.IngestJobResult> fCreate = Promise.promise();
                getIngestJob().onEachExecutionEnd(fCreate);
                fCreate.future().onComplete(context.asyncAssertSuccess(results2->{
                    context.assertEquals(1, results2.getSucceed().size());
                }));
            }else{
                context.assertEquals(1, results.getSucceed().size());
            }
        }));
    }


    @Test
    public void testShouldExploreResourceByFolder(TestContext context) {
        final Async async = context.async(2);
        final UserInfos user2 = test.directory().generateUser("user2");
        final UserInfos user = test.http().sessionUser();
        final ExplorerService.ExplorerMessageBuilder message1 = create(user, "idexplore1", "name1", "text1");
        final ExplorerService.ExplorerMessageBuilder message2 = create(user, "idexplore2", "name2", "text2");
        final ExplorerService.ExplorerMessageBuilder message3 = create(user2, "idexplore3", "name3", "text3");
        final ExplorerService.ExplorerMessageBuilder message2_1 = create(user, "idexplore2_1", "name2_1", "text2_1");
        //user1 has 3 resources and user2 has 1
        getExplorerService().push(Arrays.asList(message1, message2, message3, message2_1)).onComplete(context.asyncAssertSuccess(push -> {
            getIngestJob().execute(true).onComplete(context.asyncAssertSuccess(load -> {
                //user1 see 3 resource at root
                getResourceService().fetch(user, "blog", new ResourceService.SearchOperation().setTrashed(false)).onComplete(context.asyncAssertSuccess(fetch1 -> {
                    context.assertEquals(3, fetch1.size());
                    final JsonObject json = fetch1.stream().map(e -> (JsonObject) e).filter(e -> e.getString("_id").equals("idexplore2_1")).findFirst().get();
                    //user1 move 1 resource to folder1
                    getResourceService().move(user, json, Optional.empty(), Optional.of("folder1")).onComplete(context.asyncAssertSuccess(move -> {
                        //user1 see 1 resource at folder1
                        getResourceService().fetch(user, "blog", new ResourceService.SearchOperation().setParentId(Optional.of("folder1"))).onComplete(context.asyncAssertSuccess(fetch2 -> {
                            context.assertEquals(1, fetch2.size());
                            async.countDown();
                        }));
                        //user1 see 2 resource at root
                        getResourceService().fetch(user, "blog", new ResourceService.SearchOperation().setParentId(Optional.empty())).onComplete(context.asyncAssertSuccess(fetch2 -> {
                            context.assertEquals(2, fetch2.size());
                            async.countDown();
                        }));
                    }));
                }));
                //user1 has 1 resource with text -> text2
                getResourceService().fetch(user, "blog", new ResourceService.SearchOperation().setSearch("text2")).onComplete(context.asyncAssertSuccess(fetch1 -> {
                    context.assertEquals(1, fetch1.size());
                }));
            }));
        }));
    }

    @Test
    public void testShouldExploreResourceByShare(TestContext context) {
        final JsonObject rights = new JsonObject().put("read", true).put("contrib", true).put("manage", true);
        final Async async = context.async(2);
        final UserInfos user1 = test.directory().generateUser("user_share1", "group_share1");
        final UserInfos user2 = test.directory().generateUser("user_share2", "group_share2");
        final ExplorerService.ExplorerMessageBuilder message1 = create(user1, "idshare1", "name1", "text1");
        final ExplorerService.ExplorerMessageBuilder message2 = create(user1, "idshare2", "name2", "text2");
        final ExplorerService.ExplorerMessageBuilder message3 = create(user1, "idshare3", "name3", "text3");
        //load documents
        getExplorerService().push(Arrays.asList(message1, message2, message3)).compose((push -> {
            return getIngestJob().execute(true).compose((load -> {
                //user1 see 3 resources
                return getResourceService().fetch(user1, "blog", new ResourceService.SearchOperation()).compose((fetch1 -> {
                    context.assertEquals(3, fetch1.size());
                    return Future.succeededFuture();
                }));
            })).compose(r -> {
                //user2 see 0 resources
                return getResourceService().fetch(user2, "blog", new ResourceService.SearchOperation()).compose((fetch1 -> {
                    context.assertEquals(0, fetch1.size());
                    return Future.succeededFuture();
                }));
            });
        })).compose(rr -> {
            //share doc1 to user2
            try {
                final JsonObject doc1 = new JsonObject().put("application", "blog").put("_id", "idshare1");
                final List<ResourceService.ShareOperation> shares1 = shareTo(rights, user2);
                return getResourceService().share(user1, doc1, shares1).compose((share1 -> {
                    //user1 see 3 resources
                    return getResourceService().fetch(user1, "blog", new ResourceService.SearchOperation()).compose((fetch2 -> {
                        context.assertEquals(3, fetch2.size());
                        return Future.succeededFuture();
                    }));
                })).compose((r -> {
                    //user2 see 1 resources
                    return getResourceService().fetch(user2, "blog", new ResourceService.SearchOperation()).compose((fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        return Future.succeededFuture();
                    }));
                }));
            } catch (Exception e) {
                context.fail(e);
                return Future.failedFuture(e);
            }
        }).compose(rr -> {
            //share doc2 to group2
            try {
                final List<ResourceService.ShareOperation> shares2 = shareToGroup(rights, "group_share1", "group_share2", "group_share3", "group_share4", "group_share5", "group_share6");
                final JsonObject doc2 = new JsonObject().put("application", "blog").put("_id", "idshare2");
                return getResourceService().share(user1, doc2, shares2).compose((share2 -> {
                    //user1 see 3 resources
                    return getResourceService().fetch(user1, "blog", new ResourceService.SearchOperation()).compose((fetch3 -> {
                        context.assertEquals(3, fetch3.size());
                        async.countDown();
                        return Future.succeededFuture();
                    }));
                })).compose(rrrr -> {
                    //user2 see 2 resources
                    return getResourceService().fetch(user2, "blog", new ResourceService.SearchOperation()).compose((fetch3 -> {
                        context.assertEquals(2, fetch3.size());
                        async.countDown();
                        return Future.succeededFuture();
                    }));
                });
            } catch (Exception e) {
                context.fail(e);
                return Future.failedFuture(e);
            }
        }).onComplete(context.asyncAssertSuccess(r -> {
            System.out.println("end of testShouldExploreResourceByShare");
        }));
    }

    @Test
    public void testShouldSearchResourceWithComplexContent(TestContext context) {
        //TODO
    }

    @Test
    public void testShouldSearchResourceWithComplexCriteria(TestContext context) {
        //TODO
    }

}
