package com.opendigitaleducation.explorer.controllers;

import com.opendigitaleducation.explorer.Explorer;
import com.opendigitaleducation.explorer.filters.FolderFilter;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.plugin.ExplorerPlugin;
import com.opendigitaleducation.explorer.plugin.ExplorerPluginClient;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import fr.wseduc.webutils.validation.JsonSchemaValidator;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ExplorerController extends BaseController {
    private static final JsonSchemaValidator validator = JsonSchemaValidator.getInstance();
    private static final String USERBOOK_ADDRESS = "userbook.preferences";
    private static final Logger log = LoggerFactory.getLogger(ExplorerController.class);
    private final EventHelper eventHelper;
    private final FolderService folderService;
    private final ResourceService resourceService;

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
            getQueryParams("getContext", request.params()).onSuccess(queryParams -> {
                final JsonObject json = new JsonObject();
                json.put("preferences", new JsonObject());
                json.put("folders", new JsonArray());
                json.put("filters", new JsonArray());
                json.put("orders", new JsonArray());
                json.put("actions", new JsonArray());
                json.put("pagination", new JsonArray());
                json.put("resources", new JsonArray());
                //load root folders
                final Optional<String> folderId = Optional.ofNullable(queryParams.getString("folder"));
                final Future<JsonArray> folders = folderService.fetch(user, folderId).onSuccess(e -> {
                    json.put("folders", adaptFolder(e));
                });
                //load user preferences from neo4j
                final String application = queryParams.getString("application");
                final Future<JsonArray> preferences = getUserPref(request, application).compose(pref -> {
                    //load filters from conf and pref
                    final JsonArray filters = config.getJsonArray("filters", new JsonArray());
                    for (final Object filterO : filters) {
                        final JsonObject filter = (JsonObject) filterO;
                        final String id = filter.getString("id");
                        filter.put("defaultValue", pref.getValue("filters." + id, filter.getValue("defaultValue")));
                    }
                    json.put("filters", filters);
                    //load orders from conf and pref
                    final JsonArray orders = config.getJsonArray("orders", new JsonArray());
                    for (final Object ordersO : orders) {
                        final JsonObject order = (JsonObject) ordersO;
                        final String id = order.getString("id");
                        order.put("defaultValue", pref.getValue("orders." + id, order.getValue("defaultValue")));
                    }
                    json.put("orders", orders);
                    //load actions from conf
                    final JsonArray actions = config.getJsonArray("actions", new JsonArray());
                    for (final Object actionsO : actions) {
                        final JsonObject action = (JsonObject) actionsO;
                        final String workflow = action.getString("workflow");
                        final boolean available = user.getAuthorizedActions().stream().noneMatch(a -> a.getName().equals(workflow));
                        action.put("available", available);
                    }
                    json.put("actions", actions);
                    json.put("preferences", pref);
                    //load root resource using filters
                    return resourceService.fetch(user, application, toResourceSearch(queryParams)).onSuccess(e -> {
                        json.put("resources", adaptResource(e));
                    });
                });
                //TODO pagination details
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
            getQueryParams("getContext", request.params()).onSuccess(queryParams -> {
                final JsonObject json = new JsonObject();
                final Optional<String> folderId = Optional.ofNullable(queryParams.getString("folder"));
                final Future<JsonArray> folders = folderService.fetch(user, folderId).onSuccess(e -> {
                    json.put("folders", adaptFolder(e));
                });
                final String application = queryParams.getString("application");
                final Future<JsonArray> resourcesF = resourceService.fetch(user, application, toResourceSearch(queryParams)).onSuccess(e -> {
                    json.put("resources", adaptResource(e));
                });
                //TODO pagination details
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
            folderService.fetch(user, folderId).onSuccess(e -> {
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
                folderService.create(user, body).onSuccess(e -> {
                    body.put("id", e);
                    body.put("childNumber", 0);
                    body.put("createdAt", new Date().getTime());
                    renderJson(request, body);
                }).onFailure(e -> {
                    renderError(request);
                    log.error("Failed to create folders:", e);
                });
            });
        });
    }

    @Put("folders/:id/move")
    @SecuredAction(value = "explorer.contrib", type = ActionType.RESOURCE)
    @ResourceFilter(FolderFilter.class)
    public void moveToFolder(final HttpServerRequest request) {
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
            RequestUtils.bodyToJson(request, pathPrefix + "moveFolder", body -> {
                folderService.update(user, id, body).onSuccess(e -> {
                    //TODO response details?
                    body.mergeIn(e);
                    renderJson(request, body);
                }).onFailure(e -> {
                    renderError(request);
                    log.error("Failed to create folders:", e);
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
            RequestUtils.bodyToJson(request, pathPrefix + "createFolder", body -> {
                folderService.update(user, id, body).onSuccess(e -> {
                    //TODO response details?
                    body.mergeIn(e);
                    renderJson(request, body);
                }).onFailure(e -> {
                    renderError(request);
                    log.error("Failed to create folders:", e);
                });
            });
        });
    }

    @Delete("folders")
    @ResourceFilter(FolderFilter.class)
    @SecuredAction(value = "explorer.contrib", type = ActionType.RESOURCE)
    public void deleteFolders(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            RequestUtils.bodyToJson(request, pathPrefix + "deleteFolder", body -> {
                //TODO resource delete
                final Set<String> ids = body.getJsonArray("folderIds").stream().map(e -> e.toString()).collect(Collectors.toSet());
                folderService.delete(user, ids).onSuccess(e -> {
                    //TODO response details
                    renderJson(request, new JsonObject().put("details", new JsonArray(e)));
                }).onFailure(e -> {
                    renderError(request);
                    log.error("Failed to create folders:", e);
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

    @Get("reindex/:application/:type")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void reindex(final HttpServerRequest request) {
        final String app = request.params().get("application");
        final String type = request.params().get("type");
        final ExplorerPluginClient client =  ExplorerPluginClient.withBus(vertx, app, type);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            final SimpleDateFormat format = new SimpleDateFormat("HHmm-ddMMyyyy");
            final Optional<String> from = Optional.ofNullable(request.params().get("from"));
            final Optional<String> to = Optional.ofNullable(request.params().get("to"));
            try {
                final Optional<Date>  fromDate = from.isPresent()? Optional.of(format.parse(from.get())):Optional.empty();
                final Optional<Date>  toDate =to.isPresent()?Optional.of(format.parse(to.get())):Optional.empty();
                client.getForIndexation(user, fromDate, toDate).onComplete(e->{
                   if(e.succeeded()){
                       renderJson(request, e.result().toJson());
                   }else{
                       renderError(request, new JsonObject().put("error", e.cause().getMessage()));
                   }
                });
            } catch (ParseException e) {
                badRequest(request, e.getMessage());
            }
        });
    }

    //TODO on batch delete / update / return list of succeed and list of failed
    private ResourceService.SearchOperation toResourceSearch(final JsonObject queryParams) {
        //TODO all criterias
        final Optional<String> folderId = Optional.ofNullable(queryParams.getString("folder"));
        final ResourceService.SearchOperation op = new ResourceService.SearchOperation();
        op.setParentId(folderId);
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
        folder.put("childNumber", folder.getJsonArray("childrenIds", new JsonArray()).size());
        return folder;
    }

    private JsonArray adaptResource(final JsonArray folders) {
        final JsonArray res = new JsonArray();
        for (final Object o : folders) {
            res.add(adaptResource((JsonObject) o));
        }
        return res;
    }

    private JsonObject adaptResource(final JsonObject folder) {
        return folder;
    }

    //TODO move to userutils?
    private Future<JsonObject> getUserPref(final HttpServerRequest request, final String application) {
        final Promise<JsonObject> promise = Promise.promise();
        final JsonObject params = new JsonObject().put("action", "get.currentuser")
                .put("request", new JsonObject().put("headers", new JsonObject().put("Cookie", request.getHeader("Cookie"))))
                .put("application", "theme");
        eb.request(USERBOOK_ADDRESS, params, (final AsyncResult<Message<JsonObject>> event) -> {
            if (event.succeeded()) {
                final JsonObject body = event.result().body();
                if ("error".equals(body.getString("status"))) {
                    promise.fail(body.getString("error"));
                } else {
                    promise.complete(body.getJsonObject("value", new JsonObject()));
                }
            } else {
                promise.fail(event.cause());
            }
        });
        return promise.future();
    }

    //TODO move to webutils? RequestUtils.getQueryParams
    private Future<JsonObject> getQueryParams(final String schema, final MultiMap queryParams) {
        final JsonObject json = new JsonObject();
        for (final String name : queryParams.names()) {
            final List<String> values = queryParams.getAll(name);
            if (values.size() == 1) {
                json.put(name, values.iterator().next());
            } else {
                json.put(name, new JsonArray(values));
            }
        }
        //validate
        final Promise<JsonObject> promise = Promise.promise();
        validator.validate(pathPrefix + schema, json, res -> {
            if (res.succeeded()) {
                final JsonObject body = res.result().body();
                if ("ok".equals(body.getString("status"))) {
                    promise.complete(json);
                } else {
                    promise.fail(body.getString("message"));
                }
            } else {
                log.error("Validate async error.", res.cause());
                promise.fail(res.cause());
            }
        });
        return promise.future();
    }
}
