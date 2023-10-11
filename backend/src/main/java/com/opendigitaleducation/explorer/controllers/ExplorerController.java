package com.opendigitaleducation.explorer.controllers;

import com.opendigitaleducation.explorer.Explorer;
import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.filters.MoveFilter;
import com.opendigitaleducation.explorer.filters.ShareFilter;
import com.opendigitaleducation.explorer.filters.UpdateFilter;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.services.FolderSearchOperation;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.tasks.MigrateCronTask;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import static fr.wseduc.webutils.request.RequestUtils.getDateParam;
import static fr.wseduc.webutils.request.RequestUtils.getParamAsSet;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import static java.util.Collections.emptySet;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.to.ExplorerReindexResourcesRequest;
import org.entcore.common.explorer.to.TrashRequest;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.HttpUtils;
import org.entcore.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ExplorerController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(ExplorerController.class);
    private final EventHelper eventHelper;
    private final FolderService folderService;
    private final ResourceService resourceService;
    static long DEFAULT_PAGESIZE = 20L;

    public ExplorerController(final FolderService folderService, final ResourceService resourceService) {
        final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Explorer.class.getSimpleName());
        this.eventHelper = new EventHelper(eventStore);
        this.folderService = folderService;
        this.resourceService = resourceService;
    }

    @Get("")
    @SecuredAction("explorer.view")
    public void view(HttpServerRequest request) {
        renderView(request);
        eventHelper.onAccess(request);
    }

    //TODO poc only
    @Get("list")
    @SecuredAction("explorer.view")
    public void list(HttpServerRequest request) {
        renderView(request, new JsonObject(), "explorer-list.html", null);
    }

    @Get("context")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getContext(final HttpServerRequest request) {
        //context?application=&resource_type=&id=&order_by=name:asc|desc&owner=true|false&shared=true|false&public=true|false&favorite=true|false&folder=id
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            HttpUtils.getAndCheckQueryParams(pathPrefix,"getContext", request.params()).onSuccess(queryParams -> {
                final String application = queryParams.getString("application");
                final JsonObject json = new JsonObject();
                json.put("preferences", new JsonObject());
                json.put("folders", new JsonArray());
                json.put("filters", new JsonArray());
                json.put("orders", new JsonArray());
                json.put("actions", new JsonArray());
                json.put("pagination", new JsonArray());
                json.put("resources", new JsonArray());
                //load root folders
                final Future<JsonArray> folders = folderService.fetch(user, application, toFolderSearch(queryParams)).onSuccess(e -> {
                    json.put("folders", adaptFolder(e));
                });
                //load user preferences from neo4j
                final Future<ResourceService.FetchResult> preferences = UserUtils.getUserPreferences(eb, request, application).compose(pref -> {
                    //load orders from conf and pref
                    json.put("preferences", pref);
                    //load root resource using filters
                    final ResourceSearchOperation searchOperation = toResourceSearch(queryParams);
                    return resourceService.fetchWithMeta(user, application, searchOperation).onSuccess(e -> {
                        final List<JsonObject> resources = e.rows;
                        json.put("resources", adaptResource(resources));
                        //pagination details
                        final JsonObject pagination = new JsonObject().put("startIdx", searchOperation.getStartIndex().orElse(0L));
                        pagination.put("pageSize", searchOperation.getPageSize().orElse(DEFAULT_PAGESIZE));
                        pagination.put("maxIdx", e.count);
                        json.put("pagination", pagination);
                    });
                });
                //wait all
                CompositeFuture.all(folders, preferences).onComplete(e -> {
                    if (e.succeeded()) {
                        renderJson(request, json);
                    } else {
                        renderError(request);
                        log.error("Failed to fetch context:", e.cause());
                    }
                });
            }).onFailure(e -> {
                badRequest(request, e.getMessage());
            });
        });
    }

    //resources?application=&resource_type=&id=&order_by=name:asc|desc&owner=true|false&public=true|false&shared=true|false&favorite=true|false&folder=id&search=FULL_TEXT_SEARCH&start_idx=X&page_size=Y
    @Get("resources")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getResources(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            HttpUtils.getAndCheckQueryParams(pathPrefix,"getContext", request.params()).onSuccess(queryParams -> {
                final String application = queryParams.getString("application");
                final JsonObject json = new JsonObject();
                json.put("searchConfig", config.getJsonObject("search-config", new JsonObject()));
                final Future<JsonArray> folders = folderService.fetch(user, application, toFolderSearch(queryParams)).onSuccess(e -> {
                    json.put("folders", adaptFolder(e));
                });
                final ResourceSearchOperation searchOperation = toResourceSearch(queryParams);
                final Future<ResourceService.FetchResult> resourcesF = resourceService.fetchWithMeta(user, application, searchOperation).onSuccess(e -> {
                    final List<JsonObject> resources = e.rows;
                    json.put("resources", adaptResource(resources));
                    //pagination details
                    final JsonObject pagination = new JsonObject().put("startIdx", searchOperation.getStartIndex().orElse(0L));
                    pagination.put("pageSize", searchOperation.getPageSize().orElse(DEFAULT_PAGESIZE));
                    pagination.put("maxIdx", e.count);
                    json.put("pagination", pagination);
                });
                //wait all
                CompositeFuture.all(folders, resourcesF).onComplete(e -> {
                    if (e.succeeded()) {
                        renderJson(request, json);
                    } else {
                        renderError(request);
                        log.error("Failed to fetch resources:", e.cause());
                    }
                });
            }).onFailure(e -> {
                badRequest(request, e.getMessage());
            });
        });
    }

    @Get("folders")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getFolders(final HttpServerRequest request) {
        getFoldersById(request);
    }

    @Get("folders/:id")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getFoldersById(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            final JsonObject json = new JsonObject();
            final Optional<String> folderId = Optional.ofNullable(request.params().get("id"));
            folderService.fetch(user, Optional.empty(), folderId).onSuccess(e -> {
                json.put("folders", adaptFolder(e));
            }).onComplete(e -> {
                if (e.succeeded()) {
                    renderJson(request, json);
                } else {
                    renderError(request);
                    log.error("Failed to fetch folders:", e.cause());
                }
            });
        });
    }

    @Post("folders")
    @SecuredAction(value = "explorer.folders.create", type = ActionType.WORKFLOW)
    public void createFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "createFolder", body -> {
                final String application = body.getString("application");
                if (StringUtils.isEmpty(application)) {
                    badRequest(request, "missing.application");
                    return;
                }
                folderService.create(user,application, body).onSuccess(e -> {
                    body.put("id", e);
                    body.put("childNumber", 0);
                    body.put("createdAt", new Date().getTime());
                    renderJson(request, adaptFolder(body));
                }).onFailure(e -> {
                    badRequest(request, e.getMessage());
                    log.error("Failed to create folders:", e);
                });
            });
        });
    }


    @Put("folders/:id")
    @SecuredAction(value = "explorer.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(UpdateFilter.class)
    public void updateFolder(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            final String id = request.params().get("id");
            if (StringUtils.isEmpty(id)) {
                badRequest(request, "missing.id");
                return;
            }
            if (ExplorerConfig.ROOT_FOLDER_ID.equalsIgnoreCase(id)) {
                badRequest(request, "bad.id");
                return;
            }
            if (ExplorerConfig.BIN_FOLDER_ID.equalsIgnoreCase(id)) {
                badRequest(request, "bad.id");
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "createFolder", body -> {
                final String application = body.getString("application");
                if (StringUtils.isEmpty(application)) {
                    badRequest(request, "missing.application");
                    return;
                }
                folderService.update(user, id, application, body).onSuccess(e -> {
                    body.mergeIn(e);
                    renderJson(request, adaptFolder(body));
                }).onFailure(e -> {
                    badRequest(request, e.getMessage());
                    log.error("Failed to create folders:", e);
                });
            });
        });
    }

    @Post("folders/:id/move")
    //check foldes and resources inside service method
    @SecuredAction(value = "explorer.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(MoveFilter.class)
    public void moveBatch(final HttpServerRequest request) {
        //same for delete
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            final String id = request.params().get("id");
            if (StringUtils.isEmpty(id)) {
                badRequest(request, "missing.id");
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "moveBatch", body -> {
                final Optional<String> dest = Optional.ofNullable(id);
                final Set<Integer> resourceIds = body.getJsonArray("resourceIds").stream().map(e->(String)e).map(e-> Integer.valueOf(e)).collect(Collectors.toSet());
                final Set<String> folderIds = body.getJsonArray("folderIds").stream().map(e->(String)e).collect(Collectors.toSet());
                final String application = body.getString("application");
                final List<Future> futures = new ArrayList<>();
                final JsonObject results = new JsonObject();
                futures.add(folderService.move(user, folderIds, application, dest).onSuccess(all->{
                    final List<JsonObject> transformed = all.stream().map(fold-> adaptFolder(fold)).collect(Collectors.toList());
                    results.put("folders",new JsonArray(transformed));
                }));
                futures.add(resourceService.move(user, application, resourceIds, dest).onSuccess(all->{
                    final List<JsonObject> alls = all.stream().map(json-> adaptResource((JsonObject)json)).collect(Collectors.toList());
                    results.put("resources",new JsonArray(alls));
                }));
                CompositeFuture.all(futures).onComplete(res->{
                    if(res.succeeded()){
                        renderJson(request, results);
                    }else{
                        badRequest(request, res.cause().getMessage());
                    }
                });
            });
        });
    }



    @Put("trash")
    //check folders and resources inside service method
    @SecuredAction(value = "explorer.contrib", type = ActionType.AUTHENTICATED)
    public void trashBatch(final HttpServerRequest request) {
        trashBatch(request, true);
    }
    @Put("restore")
    //check folders and resources inside service method
    @SecuredAction(value = "explorer.contrib", type = ActionType.AUTHENTICATED)
    public void restoreBatch(final HttpServerRequest request){
        trashBatch(request, false);
    }

    private void trashBatch(final HttpServerRequest request, final boolean isTrashed){
        //same for delete
        UserUtils.getAuthenticatedUserInfos(eb, request).onSuccess(user -> {
            RequestUtils.bodyToJson(request, pathPrefix + "trashBatch", body -> {
                final TrashRequest trashRequest = body.mapTo(TrashRequest.class);
                final Set<Integer> resourceIds = trashRequest.getResourceIds().stream().map(Integer::valueOf).collect(Collectors.toSet());
                final Set<String> folderIds = trashRequest.getFolderIds();
                final String application = trashRequest.getApplication();
                final List<Future> futures = new ArrayList<>();
                final JsonObject results = new JsonObject();
                futures.add(folderService.trash(user, folderIds, application, isTrashed).onSuccess(all->{
                    final List<JsonObject> transformed = all.stream().map(this::adaptFolder).collect(Collectors.toList());
                    results.put("folders",new JsonArray(transformed));
                }));
                futures.add(resourceService.trash(user, application, resourceIds, isTrashed).onSuccess(all->{
                    final List<JsonObject> alls = all.stream().map(json-> adaptResource((JsonObject)json)).collect(Collectors.toList());
                    results.put("resources",new JsonArray(alls));
                }));
                CompositeFuture.all(futures).onComplete(res->{
                    if(res.succeeded()){
                        renderJson(request, results);
                    }else{
                        badRequest(request, res.cause().getMessage());
                    }
                });
            });
        });
    }

    @Delete("")
    //check foldes and resources inside service method
    @SecuredAction(value = "explorer.contrib", type = ActionType.AUTHENTICATED)
    public void deleteBatch(final HttpServerRequest request) {
        //same for delete
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "deleteBatch", body -> {
                final Set<String> resourceIds = body.getJsonArray("resourceIds").stream().map(e->(String)e).collect(Collectors.toSet());
                final Set<String> folderIds = body.getJsonArray("folderIds").stream().map(e->(String)e).collect(Collectors.toSet());
                final String application = body.getString("application");
                final String resourceType = body.getString("resourceType", application);
                final List<Future> futures = new ArrayList<>();
                final JsonObject results = new JsonObject();
                futures.add(folderService.delete(user, application, folderIds).onSuccess(all->{
                    final List<JsonObject> json = all.stream().map(id -> adaptFolder(new JsonObject().put("id",id))).collect(Collectors.toList());
                    results.put("folders", new JsonArray(json));
                }));
                futures.add(resourceService.delete(user, application, resourceType, resourceIds).onSuccess(all->{
                    final List<JsonObject> jsons = all.stream().map(json->adaptResource((JsonObject)json)).collect(Collectors.toList());
                    results.put("resources", new JsonArray(jsons));

                }));
                CompositeFuture.all(futures).onComplete(res->{
                    if(res.succeeded()){
                        renderJson(request, results);
                    }else{
                        badRequest(request, res.cause().getMessage());
                    }
                });
            });
        });
    }

    @Put("/share/:application/:type/:resourceId")
    @ResourceFilter(ShareFilter.class)
    @SecuredAction(value = "explorer.manager", type = ActionType.RESOURCE)
    public void shareResource(final HttpServerRequest request) {
        final String resourceId = request.params().get("resourceId");
        final String app = request.params().get("application");
        final String type = request.params().get("type");
        final IExplorerPluginClient client = IExplorerPluginClient.withBus(vertx, app, type);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, share -> {
                //TODO map virtual rights?
                client.shareById(user, resourceId, share).onComplete(r -> {
                    if (r.succeeded()) {
                        final JsonArray nta = r.result().notifyTimelineMap.get(resourceId);
                        renderJson(request, new JsonObject().put("notify-timeline-array", nta));
                    } else {
                        final JsonObject error = new JsonObject().put("error", r.cause().getMessage());
                        renderJson(request, error, 400);
                    }
                });
            });
        });
    }

    @Get("metrics")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getMetrics(final HttpServerRequest request) {
        final DeliveryOptions opt = new DeliveryOptions().addHeader("action", IngestJob.INGESTOR_JOB_METRICS).setSendTimeout(60000);
        eb.request(IngestJob.INGESTOR_JOB_ADDRESS, new JsonObject(), opt, e -> {
            if (e.succeeded()) {
                renderJson(request, (JsonObject) e.result().body());
            } else {
                renderError(request, new JsonObject().put("error", e.cause().getMessage()));
            }
        });
    }

    @Get("simulate/resources")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getSimulatedResources(final HttpServerRequest request) {
        final UserInfos user = new UserInfos();
        user.setUserId(request.params().get("userid"));
        HttpUtils.getAndCheckQueryParams(pathPrefix,"getContext", request.params()).onSuccess(queryParams -> {
            final String application = queryParams.getString("application");
            final JsonObject json = new JsonObject();
            final Future<JsonArray> folders = folderService.fetch(user, application, toFolderSearch(queryParams)).onSuccess(e -> {
                json.put("folders", adaptFolder(e));
            });
            final ResourceSearchOperation searchOperation = toResourceSearch(queryParams);
            final Future<ResourceService.FetchResult> resourcesF = resourceService.fetchWithMeta(user, application, searchOperation).onSuccess(e -> {
                json.put("resources", adaptResource(e.rows));
                //pagination details
                final JsonObject pagination = new JsonObject().put("startIdx", searchOperation.getStartIndex().orElse(0l));
                pagination.put("pageSize", searchOperation.getPageSize().orElse(DEFAULT_PAGESIZE));
                pagination.put("maxIdx", e.count);
                json.put("pagination", pagination);
            });
            //wait all
            CompositeFuture.all(folders, resourcesF).onComplete(e -> {
                if (e.succeeded()) {
                    renderJson(request, json);
                } else {
                    renderError(request);
                    log.error("Failed to fetch resources:", e.cause());
                }
            });
        }).onFailure(e -> {
            badRequest(request, e.getMessage());
        });
    }

    @Get("simulate/folders/:id")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getSimulateFoldersById(final HttpServerRequest request) {
        final UserInfos user = new UserInfos();
        user.setUserId(request.params().get("userid"));
        final JsonObject json = new JsonObject();
        final Optional<String> folderId = Optional.ofNullable(request.params().get("id"));
        folderService.fetch(user, Optional.empty(), folderId).onSuccess(e -> {
            json.put("folders", adaptFolder(e));
        }).onComplete(e -> {
            if (e.succeeded()) {
                renderJson(request, json);
            } else {
                renderError(request);
                log.error("Failed to fetch folders:", e.cause());
            }
        });
    }


    @Get("job/trigger")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void triggerJob(final HttpServerRequest request) {
        final DeliveryOptions opt = new DeliveryOptions().addHeader("action", IngestJob.INGESTOR_JOB_TRIGGER);
        if (request.params().contains("timeout")) {
            final long timeout = Long.valueOf(request.params().get("timeout"));
            opt.setSendTimeout(timeout);
        }
        eb.request(IngestJob.INGESTOR_JOB_ADDRESS, new JsonObject(), opt, e -> {
            if (e.succeeded()) {
                renderJson(request, (JsonObject) e.result().body());
            } else {
                renderError(request, new JsonObject().put("error", e.cause().getMessage()));
            }
        });
    }


    @Get("job/status/:method")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void jobStatus(final HttpServerRequest request) {
        final String method = request.params().get("method");
        final DeliveryOptions opt = new DeliveryOptions().addHeader("action", IngestJob.INGESTOR_STATUS).addHeader("method", method);
        if("start".equalsIgnoreCase(method) || "stop".equalsIgnoreCase(method) || "get".equalsIgnoreCase(method)) {
            eb.request(IngestJob.INGESTOR_JOB_ADDRESS, new JsonObject(), opt, e -> {
                if (e.succeeded()) {
                    renderJson(request, (JsonObject) e.result().body());
                } else {
                    renderError(request, new JsonObject().put("error", e.cause().getMessage()));
                }
            });
        }else{
            badRequest(request);
        }
    }

    @Get("reindex/:application/:type")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void reindex(final HttpServerRequest request) {
        final String app = request.params().get("application");
        final String type = request.params().get("type");
        final String drop = request.params().get("drop");
        final String oldFolders = request.params().get("include_old_folders");
        final String newFolders = request.params().get("include_new_folders");
        if("all".equals(app)){
            final Set<String> apps = config.getJsonArray("applications").stream().map(Object::toString).collect(Collectors.toSet());
            final boolean dropBefore= "true".equals(drop) || drop == null;
            final boolean oldFolder = "true".equals(oldFolders) || oldFolders == null;
            final boolean newFolder = "true".equals(newFolders) || newFolders == null;
            new MigrateCronTask(this.vertx, this.resourceService, apps ,dropBefore, oldFolder, newFolder).run().onComplete(onFinish -> {
                if (onFinish.succeeded()) {
                    renderJson(request,onFinish.result());
                } else {
                    renderError(request, new JsonObject().put("error", onFinish.cause().getMessage()));
                }
            });
        }else {
            final IExplorerPluginClient client = IExplorerPluginClient.withBus(vertx, app, type);
            UserUtils.getUserInfos(eb, request, user -> {
                if (user == null) {
                    unauthorized(request);
                    return;
                }
                final boolean includeFolder = "true".equalsIgnoreCase(request.params().get("include_folders"));
                try {
                    final Future<Void> dropFuture = "true".equals(drop) ? resourceService.dropMapping(app).compose(e -> {
                        return resourceService.initMapping(app);
                    }) : Future.succeededFuture();
                    final Date fromDate = getDateParam("from", request).orElse(null);
                    final Date toDate = getDateParam("to", request).orElse(null);
                    final Set<String> ids = getParamAsSet("ids", request);
                    final Future<IExplorerPluginClient.IndexResponse> future = dropFuture.compose(e -> {
                        final ExplorerReindexResourcesRequest reindexRequest =
                                new ExplorerReindexResourcesRequest(fromDate, toDate, emptySet(), includeFolder, ids);
                        return client.reindex(user, reindexRequest);
                    });
                    future.onComplete(res -> {
                        if (res.succeeded()) {
                            renderJson(request, res.result().toJson());
                        } else {
                            renderError(request, new JsonObject().put("error", res.cause().getMessage()));
                        }
                    });
                } catch (IllegalArgumentException e) {
                    badRequest(request, e.getMessage());
                }
            });
        }
    }

    //TODO on batch delete / update / return list of succeed and list of failed
    private ResourceSearchOperation toResourceSearch(final JsonObject queryParams) {
        ///application=&resource_type=&id=&order_by=name:asc|desc&owner=true|false&public=true|false&shared=true|false&favorite=true|false&folder=id&search=FULL_TEXT_SEARCH&start_idx=X&page_size=Y&search_after=name|createdAt
        final Optional<String[]> order = Optional.ofNullable(queryParams.getString("order_by")).map(e-> e.split(":")).map(e -> e.length==2? e: null);
        final Optional<Boolean> orderAsc = order.map(e-> "asc".equalsIgnoreCase(e[1]));
        final Optional<String> orderField = order.map(e-> e[0]);
        final ResourceSearchOperation op = new ResourceSearchOperation();
        op.setResourceType(queryParams.getString("resource_type"));
        op.setId(queryParams.getValue("id"));
        op.setAssetId(queryParams.getValue("asset_id[]"));
        op.setOrder(orderField, orderAsc);
        op.setOwner(queryParams.getBoolean("owner"));
        op.setPub(queryParams.getBoolean("public"));
        op.setShared(queryParams.getBoolean("shared"));
        op.setFavorite(queryParams.getBoolean("favorite"));
        op.setParentId(queryParams.getValue("folder"));
        op.setSearch(queryParams.getValue("search"));
        op.setPageSize(queryParams.getLong("page_size"));
        op.setStartIndex(queryParams.getLong("start_idx"));
        op.setSearchAfter(queryParams.getValue("search_after"));
        op.setTrashed(queryParams.getBoolean("trashed"));
        // in case of search search everywhere
        op.setSearchEverywhere(op.getSearch().isPresent());
        return op;
    }

    private FolderSearchOperation toFolderSearch(final JsonObject queryParams) {
        final FolderSearchOperation op = new FolderSearchOperation();
        op.setPageSize(queryParams.getLong("folder_page_size", ExplorerConfig.DEFAULT_SIZE.longValue()));
        op.setStartIndex(queryParams.getLong("folder_start_idx", 0l));
        op.setParentId(queryParams.getValue("folder"));
        op.setTrashed(queryParams.getBoolean("trashed"));
        op.setSearch(queryParams.getValue("search"));
        // in case of search search everywhere
        op.setSearchEverywhere(op.getSearch().isPresent());
        return op;
    }

    private JsonArray adaptFolder(final JsonArray folders) {
        final JsonArray res = new JsonArray();
        for (final Object o : folders) {
            res.add(adaptFolder((JsonObject) o));
        }
        return res;
    }

    private JsonObject adaptFolder(final JsonObject folder) {
        final Object id = folder.remove("_id");
        if( id != null ) {
            folder.put( "id", id );
        }
        folder.put( "assetId", folder.getValue("id"));
        folder.put("childNumber", folder.getJsonArray("childrenIds", new JsonArray()).size());
        return folder;
    }

    private JsonArray adaptResource(final List<JsonObject> folders) {
        final JsonArray res = new JsonArray();
        for (final Object o : folders) {
            res.add(adaptResource((JsonObject) o));
        }
        return res;
    }

    private JsonObject adaptResource(final JsonObject folder) {
        final Object id = folder.remove("_id");
        if( id != null ) folder.put( "id", id );
        return folder;
    }
}
