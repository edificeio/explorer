package com.opendigitaleducation.explorer.jobs;

import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientChannel;
import com.opendigitaleducation.explorer.services.ExplorerService;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import fr.wseduc.webutils.DefaultAsyncResult;
import io.reactiverse.pgclient.PgRowSet;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.reactiverse.pgclient.impl.data.JsonImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class PostgresResourceLoader implements ResourceLoader {
    static Logger log = LoggerFactory.getLogger(PostgresResourceLoader.class);
    static final int STATUS_SUCCESS = 1;
    static final int STATUS_FAIL = -1;
    static final int STATUS_PENDING = 0;
    static final int DEFAULT_BULK_SIZE = 100;
    static final int DEFAULT_JOB_MODULO = 1;
    static final int DEFAULT_JOB_REMAINDER = 1;
    private final int bulkSize;
    private final int modulo;
    private final int remainder;
    private final Vertx vertx;
    private final PostgresClientChannel pgClient;
    private final ResourceService resourceService;
    //TODO define max concurrent thread
    //TODO define max bulk size according (dynamic bulk size acording to response?)
    private boolean start = false;
    private Future<Void> running = Future.succeededFuture();
    private Handler<AsyncResult<ResourceLoaderResult>> onEnd = e -> {
    };

    public PostgresResourceLoader(final Vertx vertx, final PostgresClient postgresClient, final ResourceService resourceService, final JsonObject config) {
        this.vertx = vertx;
        this.pgClient = postgresClient.getClientChannel();
        this.resourceService = resourceService;
        this.bulkSize = config.getInteger("bulk-size", DEFAULT_BULK_SIZE);
        this.modulo = config.getInteger("modulo", DEFAULT_JOB_MODULO);
        this.remainder = config.getInteger("modulo", DEFAULT_JOB_REMAINDER);
    }

    public void start() {
        this.start = true;
        execute();
        //TODO close remove listeners? what if we start twice?
        this.pgClient.listen(ExplorerService.RESOURCE_CHANNEL, onMessage -> {
            this.execute();
        });
    }

    public void stop() {
        this.start = false;
    }

    @Override
    public void setOnEnd(final Handler<AsyncResult<ResourceLoaderResult>> handler) {
        this.onEnd = handler;
    }

    public Future<Void> execute() {
        //TODO set a max attempt?
        //TODO debounce ms? (config)
        //TODO create a job to archive this table on night?
        // lock running
        if (!this.start) {
            return Future.failedFuture("resource loader is stopped");
        }
        return running.compose(onReady -> {
            running = Future.future();
            //TODO use modulo to split between different thread
            final String query = "SELECT * FROM explorer.resource_queue ORDER BY priority DESC, created_at ASC LIMIT "+bulkSize;
            this.pgClient.preparedQuery(query, Tuple.tuple()).compose(result -> {
                return loadPendings(result);
            }).compose(bulkResults -> {
                return saveStatus(bulkResults);
            }).setHandler(e->{
                if (e.succeeded()) {
                    this.onEnd.handle(new DefaultAsyncResult<>(e.result()));
                } else {
                    this.onEnd.handle(new DefaultAsyncResult<>(e.cause()));
                    log.error("Failed loading resources to engine:", e.cause());
                }
                running.complete();
            });
            return running;
        });
    }

    protected Future<List<JsonObject>> loadPendings(final PgRowSet result) {
        final List<ResourceService.ResourceBulkOperation> resources = new ArrayList<>();
        for (final Row row : result) {
            final String resourceAction = row.getString("resource_action");
            final String id = row.getString("id");
            final JsonObject json = (JsonObject)(row.getJson("payload")).value();
            final String creatorId = json.getString("creatorId");
            json.put("_id", id);
            if (ExplorerService.RESOURCE_ACTION_CREATE.equals(resourceAction)) {
                final String userFolderId = ResourceService.getUserFolderId(creatorId, FolderService.ROOT_FOLDER_ID);
                json.put("userAndFolderIds", new JsonArray().add(userFolderId));
            }
            final ResourceService.ResourceBulkOperationType type = ResourceService.getOperationType(resourceAction);
            resources.add(new ResourceService.ResourceBulkOperation(json, type));
        }
        return this.resourceService.bulkOperations(resources);
    }

    protected Future<ResourceLoaderResult> saveStatus(final List<JsonObject> bulkResults) {
        if (bulkResults.isEmpty()) {
            return Future.succeededFuture(new ResourceLoaderResult(new ArrayList<>(), new ArrayList<>()));
        }
        //categorise
        final List<JsonObject> succeed = new ArrayList<>();
        final List<JsonObject> failed = new ArrayList<>();
        for (final JsonObject res : bulkResults) {
            final boolean success = res.getBoolean(ResourceService.SUCCESS_FIELD, false);
            if (success) {
                succeed.add(res);
            } else {
                failed.add(res);
            }
        }
        //save
        final Future<Void> future = Future.future();
        return this.pgClient.transaction().compose(transaction -> {
            if (succeed.size() > 0) {
                final List<String> ids = succeed.stream().map(e -> e.getString("_id")).collect(Collectors.toList());
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(STATUS_SUCCESS), ids);
                final String placeholder = PostgresClient.inPlaceholder(succeed, 2);
                final String query = String.format("UPDATE explorer.resource_queue SET  attempt_status=$1, attempted_count=attempted_count+1, attempted_at=NOW() WHERE id IN (%)", placeholder);
                transaction.addPreparedQuery(query, tuple);
            }
            if (failed.size() > 0) {
                final LocalDateTime now = LocalDateTime.now();
                final Map<String, Object> defaultValues = new HashMap<>();
                defaultValues.put("_attemptat", now);
                final List<String> ids = failed.stream().map(e -> e.getString("_id")).collect(Collectors.toList());
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(now), ids);
                final String placeholder = PostgresClient.inPlaceholder(failed, 2);
                final String query = String.format("UPDATE explorer.resource_queue SET  attempted_count=attempted_count+1, attempted_at=? WHERE id IN (%)", placeholder);
                transaction.addPreparedQuery(query, tuple);
                final Tuple tupleMessage = PostgresClient.insertValues(failed, Tuple.tuple(), defaultValues, "_id", FolderService.ERROR_FIELD, "_attemptat");
                final String placeholderMessage = PostgresClient.insertPlaceholders(failed, 1, "_id", FolderService.ERROR_FIELD, "_attemptat");
                final String queryMessage = String.format("INSERT INTO explorer.resource_queue_causes (id, attempt_reason, attempted_at) VALUES %s", placeholderMessage);
                transaction.addPreparedQuery(queryMessage, tupleMessage);
            }
            return transaction.commit().map(e-> new ResourceLoaderResult(succeed, failed));
        });
    }
}
