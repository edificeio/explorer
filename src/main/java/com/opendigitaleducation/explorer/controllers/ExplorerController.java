package com.opendigitaleducation.explorer.controllers;

import com.opendigitaleducation.explorer.Explorer;
import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.filters.FolderFilter;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.SearchOperation;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.HttpUtils;
import org.entcore.common.utils.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ExplorerController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(ExplorerController.class);
    private final EventHelper eventHelper;
    private final FolderService folderService;
    private final ResourceService resourceService;
    static long DEFAULT_PAGESIZE = 20l;

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
                final Optional<String> folderId = Optional.ofNullable(queryParams.getValue("folder")).map(e->e.toString());
                final Future<JsonArray> folders = folderService.fetch(user, application, folderId).onSuccess(e -> {
                    json.put("folders", adaptFolder(e));
                });
                //load user preferences from neo4j
                final JsonObject applications = this.config.getJsonObject("applications", new JsonObject());
                final JsonObject config = applications.getJsonObject(application, new JsonObject());
                final Future<ResourceService.FetchResult> preferences = UserUtils.getUserPreferences(eb, request, application).compose(pref -> {
                    //load filters from conf and pref
                    final JsonArray filters = config.getJsonArray("filters", new JsonArray());
                    final JsonArray newFilter = new JsonArray();
                    for (final Object filterO : filters) {
                        final JsonObject filter = ((JsonObject) filterO).copy();
                        final String id = filter.getString("id");
                        filter.put("defaultValue", pref.getValue("filters." + id, filter.getValue("defaultValue")));
                        newFilter.add(filter);
                    }
                    json.put("filters", newFilter);
                    //load orders from conf and pref
                    final JsonArray orders = config.getJsonArray("orders", new JsonArray());
                    final JsonArray newOrders = new JsonArray();
                    for (final Object ordersO : orders) {
                        final JsonObject order = ((JsonObject) ordersO).copy();
                        final String id = order.getString("id");
                        order.put("defaultValue", pref.getValue("orders." + id, order.getValue("defaultValue")));
                        newOrders.add(order);
                    }
                    json.put("orders", newOrders);
                    //load actions from conf
                    final JsonArray actions = config.getJsonArray("actions", new JsonArray());
                    final JsonArray newActions = new JsonArray();
                    for (final Object actionsO : actions) {
                        final JsonObject action = ((JsonObject) actionsO).copy();
                        final String workflow = action.getString("workflow");
                        final boolean available = user.getAuthorizedActions().stream().filter(a -> a.getName().equalsIgnoreCase(workflow)).count()>0;
                        action.put("available", available);
                        newActions.add(action);
                    }
                    json.put("actions", newActions);
                    json.put("preferences", pref);
                    //load root resource using filters
                    final SearchOperation searchOperation = toResourceSearch(queryParams);
                    return resourceService.fetchWithMeta(user, application, searchOperation).onSuccess(e -> {
                        json.put("resources", adaptResource(e.rows));
                        //pagination details
                        final JsonObject pagination = new JsonObject().put("startIdx", searchOperation.getStartIndex().orElse(0l));
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
                final Optional<String> folderId = Optional.ofNullable(queryParams.getValue("folder")).map(e-> e.toString());
                final Future<JsonArray> folders = folderService.fetch(user, application, folderId).onSuccess(e -> {
                    json.put("folders", adaptFolder(e));
                });
                final SearchOperation searchOperation = toResourceSearch(queryParams);
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

    @Post("folders/:id/move")
    @SecuredAction(value = "explorer.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(FolderFilter.class)
    public void moveToFolder(final HttpServerRequest request) {
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
                final Optional<String> dest = ExplorerConfig.ROOT_FOLDER_ID.equalsIgnoreCase(id)? Optional.empty():Optional.ofNullable(id);
                final Optional<Integer> destInt = dest.map(e-> Integer.valueOf(e));
                final Set<Integer> resourceIds = body.getJsonArray("resourceIds").stream().map(e->(String)e).map(e-> Integer.valueOf(e)).collect(Collectors.toSet());
                final Set<String> folderIds = body.getJsonArray("folderIds").stream().map(e->(String)e).collect(Collectors.toSet());
                final String application = body.getString("application");
                final List<Future> futures = new ArrayList<>();
                final JsonObject results = new JsonObject();
                futures.add(folderService.move(user, folderIds, application, dest).onSuccess(all->{
                    final List<JsonObject> transformed = all.stream().map(fold-> adaptFolder(fold)).collect(Collectors.toList());
                    results.put("folders",new JsonArray(transformed));
                }));
                futures.add(resourceService.move(user, application, resourceIds, destInt).onSuccess(all->{
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

    @Put("folders/:id")
    @SecuredAction(value = "explorer.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(FolderFilter.class)
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

    @Delete("")
    @SecuredAction(value = "explorer.contrib", type = ActionType.AUTHENTICATED)
    public void deleteFolders(final HttpServerRequest request) {
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
            final Optional<String> folderId = Optional.ofNullable(queryParams.getValue("folder")).map(e-> e.toString());
            final Future<JsonArray> folders = folderService.fetch(user, application, folderId).onSuccess(e -> {
                json.put("folders", adaptFolder(e));
            });
            final SearchOperation searchOperation = toResourceSearch(queryParams);
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
        final IExplorerPluginClient client =  IExplorerPluginClient.withBus(vertx, app, type);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            final SimpleDateFormat format = new SimpleDateFormat("HHmm-ddMMyyyy");
            final Optional<String> from = Optional.ofNullable(request.params().get("from"));
            final Optional<String> to = Optional.ofNullable(request.params().get("to"));
            final boolean includeFolder = "true".equalsIgnoreCase(request.params().get("include_folders"));
            try {
                final Future<Void> dropFuture = "true".equals(drop)? resourceService.dropMapping(app).compose(e->{
                    return resourceService.initMapping(app);
                }) :  Future.succeededFuture();
                final Optional<Date>  fromDate = from.isPresent()? Optional.of(format.parse(from.get())):Optional.empty();
                final Optional<Date>  toDate =to.isPresent()?Optional.of(format.parse(to.get())):Optional.empty();
                final Future<IExplorerPluginClient.IndexResponse> future = dropFuture.compose(e -> {
                    return client.getForIndexation(user, fromDate, toDate, new HashSet(), includeFolder);
                });
                future.onComplete(res->{
                   if(res.succeeded()){
                       renderJson(request, res.result().toJson());
                   }else{
                       renderError(request, new JsonObject().put("error", res.cause().getMessage()));
                   }
                });
            } catch (ParseException e) {
                badRequest(request, e.getMessage());
            }
        });
    }

    //TODO on batch delete / update / return list of succeed and list of failed
    private SearchOperation toResourceSearch(final JsonObject queryParams) {
        ///application=&resource_type=&id=&order_by=name:asc|desc&owner=true|false&public=true|false&shared=true|false&favorite=true|false&folder=id&search=FULL_TEXT_SEARCH&start_idx=X&page_size=Y&search_after=name|createdAt
        final Optional<String[]> order = Optional.ofNullable(queryParams.getString("order_by")).map(e-> e.split(":")).map(e -> e.length==2? e: null);
        final Optional<Boolean> orderAsc = order.map(e-> "asc".equalsIgnoreCase(e[1]));
        final Optional<String> orderField = order.map(e-> e[0]);
        final SearchOperation op = new SearchOperation();
        op.setResourceType(queryParams.getString("resource_type"));
        op.setId(queryParams.getValue("id"));
        op.setOrder(orderField, orderAsc);
        op.setOwner(queryParams.getBoolean("owner"));
        op.setPub(queryParams.getBoolean("public"));
        op.setShared(queryParams.getBoolean("shared"));
        op.setFavorite(queryParams.getBoolean("favorite"));
        op.setParentId(queryParams.getValue("folder"));
        op.setSearch(queryParams.getString("search"));
        op.setPageSize(queryParams.getLong("page_size"));
        op.setStartIndex(queryParams.getLong("start_idx"));
        op.setSearchAfter(queryParams.getValue("search_after"));
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
        if( id != null ) folder.put( "id", id );
        
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
