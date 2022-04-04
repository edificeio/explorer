package com.opendigitaleducation.explorer;

import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.SearchOperation;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.impl.ExplorerPlugin;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;
import org.entcore.test.TestHelper;
import org.junit.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;
import java.util.*;

public abstract class IngestJobTest {
    protected static final TestHelper test = TestHelper.helper();
    protected static String esIndex;
    protected static final String application = FakePostgresPlugin.FAKE_APPLICATION;

    @ClassRule
    public static ElasticsearchContainer esContainer = test.database().createOpenSearchContainer().withReuse(true);
    static ElasticClientManager elasticClientManager;
    protected abstract IngestJob getIngestJob();

    protected abstract ExplorerPlugin getExplorerPlugin();

    protected abstract ShareTableManager getShareTableManager();

    protected abstract ResourceService getResourceService();

    protected abstract PostgresClient getPostgresClient();

    protected FolderService getFolderService() {
        final FolderExplorerPlugin folderPlugin = new FolderExplorerPlugin(getExplorerPlugin().getCommunication(), getPostgresClient());
        final FolderService folderService = new FolderServiceElastic(elasticClientManager, folderPlugin);
        return folderService;
    }

    @BeforeClass
    public static void setUp(TestContext context) throws Exception {
        final HttpClientOptions httpOptions = new HttpClientOptions().setDefaultHost(esContainer.getHost()).setDefaultPort(esContainer.getMappedPort(9200));
        final HttpClient httpClient = test.vertx().createHttpClient(httpOptions);
        final URI[] uris = new URI[]{new URI("http://" + esContainer.getHttpHostAddress())};
        elasticClientManager = new ElasticClientManager(test.vertx(), uris);
        final Async async = context.async();
        esIndex = ExplorerConfig.DEFAULT_RESOURCE_INDEX + "_" + System.currentTimeMillis();
        ExplorerConfig.getInstance().setEsIndex(FakePostgresPlugin.FAKE_APPLICATION, esIndex);
        System.out.println("Using index: " + esIndex);
        createMapping(elasticClientManager, context, esIndex).onComplete(r -> async.complete());
    }

    static Future<Void> createMapping(ElasticClientManager elasticClientManager,TestContext context, String index) {
        final Buffer mapping = test.vertx().fileSystem().readFileBlocking("es/mappingResource.json");
        return elasticClientManager.getClient().createMapping(index, mapping);
    }

    static JsonObject create(UserInfos user, String id, String name, String content) {
        return create(user, id, name, content, false);
    }

    static JsonObject create(UserInfos user, String id, String name, String content, boolean pub) {
        final JsonObject json = new JsonObject();
        json.put("id", id);
        json.put("name", name);
        json.put("content", content);
        json.put("public", pub);
        return json;
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
        getIngestJob().stop().onComplete(e -> {
            getIngestJob().waitPending().onComplete(ee -> {
                async.complete();
            });
        });
    }

    @After
    public void after(TestContext context) {
        final Async async = context.async();
        getIngestJob().stop().onComplete(e -> {
            getIngestJob().waitPending().onComplete(ee -> {
                async.complete();
            });
        });
    }

    @Test
    public void shouldIntegrateNewResource(TestContext context) {
        final IngestJob job = getIngestJob();
        final ExplorerPlugin exPlugin = getExplorerPlugin();
        final UserInfos user = test.directory().generateUser("intergrate_res");
        final Async async = context.async();
        job.waitPending().onComplete(e-> {
            job.start().onComplete(context.asyncAssertSuccess(r -> {
                final Promise<IngestJob.IngestJobResult> fCreate = Promise.promise();
                job.onEachExecutionEnd(fCreate);
                final JsonObject message1 = create(user, "id1", "name1", "text1");
                exPlugin.notifyUpsert(user, message1).onComplete(context.asyncAssertSuccess(push -> {
                    fCreate.future().onComplete(context.asyncAssertSuccess(results -> {
                        context.assertEquals(1, results.getSucceed().size());
                        //update
                        final Promise<IngestJob.IngestJobResult> fUpdate = Promise.promise();
                        job.onEachExecutionEnd(fUpdate);
                        final JsonObject message2 = create(user, "id1", "name1_1", "text1_1");
                        exPlugin.notifyUpsert(user, message2).onComplete(context.asyncAssertSuccess(push2 -> {
                            fUpdate.future().onComplete(context.asyncAssertSuccess(results2 -> {
                                context.assertEquals(1, results2.getSucceed().size());
                                getResourceService().fetch(user, application, new SearchOperation()).onComplete(context.asyncAssertSuccess(fetch1 -> {
                                    context.assertEquals(1, fetch1.size());
                                    //delete
                                    final Promise<IngestJob.IngestJobResult> fDelete = Promise.promise();
                                    job.onEachExecutionEnd(fDelete);
                                    exPlugin.notifyDeleteById(user, "id1").onComplete(context.asyncAssertSuccess(push3 -> {
                                        fDelete.future().onComplete(context.asyncAssertSuccess(results3 -> {
                                            context.assertEquals(1, results3.getSucceed().size());
                                            getResourceService().fetch(user, application, new SearchOperation()).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                                context.assertEquals(0, fetch2.size());
                                                async.complete();
                                            }));
                                        }));
                                    }));
                                }));
                            }));
                        }));
                    }));
                }));
            }));
        });
    }

    @Test
    public void shouldIntegrateResourceOnRestart(TestContext context) {
        final UserInfos user = test.http().sessionUser();
        final JsonObject message1 = create(user, "id_restart1", "name1", "text1");
        getExplorerPlugin().notifyUpsert(user, message1).compose((push -> {
            final Promise<IngestJob.IngestJobResult> fCreate = Promise.promise();
            getIngestJob().onEachExecutionEnd(fCreate);
            getIngestJob().start();
            return fCreate.future();
        })).onComplete(context.asyncAssertSuccess(results -> {
            context.assertEquals(0, results.getFailed().size());
            //if result empty => maybe notification start at same time of first execution
            if (results.getSucceed().isEmpty()) {
                final Promise<IngestJob.IngestJobResult> fCreate = Promise.promise();
                getIngestJob().onEachExecutionEnd(fCreate);
                fCreate.future().onComplete(context.asyncAssertSuccess(results2 -> {
                    context.assertEquals(1, results2.getSucceed().size());
                }));
            } else {
                context.assertEquals(1, results.getSucceed().size());
            }
        }));
    }


    @Test
    public void shouldExploreResourceByFolder(TestContext context) {
        final Async async = context.async(3);
        final UserInfos user2 = test.directory().generateUser("user2");
        final UserInfos user1 = test.http().sessionUser();
        final JsonObject message1 = create(user1, "idexplore1", "name1", "text1");
        final JsonObject message2 = create(user1, "idexplore2", "name2", "text2");
        final JsonObject message3 = create(user2, "idexplore3", "name3", "text3");
        final JsonObject message2_1 = create(user1, "idexplore2_1", "name2_1", "text2_1");
        //user1 has 3 resources and user2 has 1
        getExplorerPlugin().notifyUpsert(user2, Arrays.asList(message3)).onComplete(context.asyncAssertSuccess(push -> {
            getExplorerPlugin().notifyUpsert(user1, Arrays.asList(message1, message2, message2_1)).onComplete(context.asyncAssertSuccess(push1 -> {
                getIngestJob().execute(true).onComplete(context.asyncAssertSuccess(load -> {
                    //user1 see 3 resource at root
                    getResourceService().fetch(user1, application, new SearchOperation().setTrashed(false)).onComplete(context.asyncAssertSuccess(fetch1 -> {
                        context.assertEquals(3, fetch1.size());
                        final JsonObject json = fetch1.stream().map(e -> (JsonObject) e).filter(e -> e.getString("assetId").equals("idexplore2_1")).findFirst().get();
                        //create folder 1
                        getFolderService().create(user1, application, Arrays.asList(FolderServiceTest.folder("folder1"))).onComplete(context.asyncAssertSuccess(folders -> {
                            context.assertEquals(1, folders.size());
                            final Integer folder1Id = folders.get(0).getInteger("id");
                            //user1 move 1 resource to folder1
                            getResourceService().move(user1, application, json, Optional.of(folder1Id.toString())).onComplete(context.asyncAssertSuccess(move -> {
                                getIngestJob().execute(true).onComplete(context.asyncAssertSuccess(load2 -> {
                                    //user1 see 1 resource at folder1
                                    getResourceService().fetch(user1, application, new SearchOperation().setParentId(folder1Id.toString())).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                        context.assertEquals(1, fetch2.size());
                                        async.countDown();
                                    }));
                                    //user1 see 2 resource at root
                                    getResourceService().fetch(user1, application, new SearchOperation().setParentId(null)).onComplete(context.asyncAssertSuccess(fetch2 -> {
                                        context.assertEquals(2, fetch2.size());
                                        async.countDown();
                                    }));
                                }));
                            }));
                        }));
                    }));
                    //user1 has 1 resource with text -> text2
                    getResourceService().fetch(user1, application, new SearchOperation().setSearch("text2")).onComplete(context.asyncAssertSuccess(fetch1 -> {
                        context.assertEquals(1, fetch1.size());
                        async.countDown();
                    }));
                }));
            }));
        }));
    }

    @Test
    public void shouldExploreResourceByShare(TestContext context) {
        final JsonObject rights = new JsonObject().put("read", true).put("contrib", true).put("manage", true);
        final Async async = context.async(3);
        final UserInfos user1 = test.directory().generateUser("user_share1", "group_share1");
        final UserInfos user2 = test.directory().generateUser("user_share2", "group_share2");
        final JsonObject message1 = create(user1, "idshare1", "name1", "text1");
        final JsonObject message2 = create(user1, "idshare2", "name2", "text2");
        final JsonObject message3 = create(user1, "idshare3", "name3", "text3");
        //load documents
        getExplorerPlugin().notifyUpsert(user1, Arrays.asList(message1, message2, message3)).compose((push -> {
            return getIngestJob().execute(true).compose((load -> {
                //user1 see 3 resources
                return getResourceService().fetch(user1, application, new SearchOperation()).compose((fetch1 -> {
                    context.assertEquals(3, fetch1.size());
                    return Future.succeededFuture(fetch1);
                }));
            })).compose(fetch1 -> {
                //user2 see 0 resources
                return getResourceService().fetch(user2, application, new SearchOperation()).compose((fetch2 -> {
                    context.assertEquals(0, fetch2.size());
                    return Future.succeededFuture();
                })).map(fetch1);
            });
        })).compose(fetch1 -> {
            //share doc1 to user2
            try {
                final JsonObject doc1 = fetch1.stream().map(e-> (JsonObject)e).filter(e->"idshare1".equals(e.getString("assetId"))).findFirst().get();
                final List<ResourceService.ShareOperation> shares1 = shareTo(rights, user2);
                return getResourceService().share(user1, application, doc1, shares1).compose(share1 -> {
                    return getIngestJob().execute(true);
                }).compose((share1 -> {
                    //user1 see 3 resources
                    return getResourceService().fetch(user1, application, new SearchOperation()).compose((fetch2 -> {
                        context.assertEquals(3, fetch2.size());
                        return Future.succeededFuture();
                    }));
                })).compose((r -> {
                    //user2 see 1 resources
                    return getResourceService().fetch(user2, application, new SearchOperation()).compose((fetch2 -> {
                        context.assertEquals(1, fetch2.size());
                        return Future.succeededFuture();
                    }));
                })).map(fetch1);
            } catch (Exception e) {
                context.fail(e);
                return Future.failedFuture(e);
            }
        }).compose(fetch1 -> {
            //share doc2 to group2
            try {
                final List<ResourceService.ShareOperation> shares2 = shareToGroup(rights, "group_share1", "group_share2", "group_share3", "group_share4", "group_share5", "group_share6");
                final JsonObject doc2 = fetch1.stream().map(e-> (JsonObject)e).filter(e->"idshare2".equals(e.getString("assetId"))).findFirst().get();
                return getResourceService().share(user1, application, doc2, shares2).compose(share1 -> {
                    return getIngestJob().execute(true);
                }).compose((share2 -> {
                    //user1 see 3 resources
                    return getResourceService().fetch(user1, application, new SearchOperation()).compose((fetch3 -> {
                        context.assertEquals(3, fetch3.size());
                        async.countDown();
                        return Future.succeededFuture();
                    }));
                })).compose(rrrr -> {
                    //user2 see 2 resources
                    return getResourceService().fetch(user2, application, new SearchOperation()).compose((fetch3 -> {
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
            System.out.println("end of shouldExploreResourceByShare");
            getExplorerPlugin().getShareInfo(new HashSet<>(Arrays.asList("idshare1","idshare2","idshare3"))).onComplete(context.asyncAssertSuccess(e->{
                context.assertEquals(1, e.get("idshare1").size());
                context.assertEquals(6, e.get("idshare2").size());
                context.assertEquals(0, e.get("idshare3").size());
                async.countDown();
            }));
        }));
    }

}
