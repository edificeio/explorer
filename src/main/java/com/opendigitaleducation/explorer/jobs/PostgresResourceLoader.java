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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PostgresResourceLoader implements ResourceLoader {
    static final int STATUS_SUCCESS = 1;
    static final int STATUS_FAIL = -1;
    static final int STATUS_PENDING = 0;
    static final int DEFAULT_BULK_SIZE = 100;
    static final int DEFAULT_JOB_MODULO = 1;
    static Logger log = LoggerFactory.getLogger(PostgresResourceLoader.class);
    private final int bulkSize;
    private final int modulo;
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
        //TODO close remove listeners?
        this.pgClient.listen(ExplorerService.RESOURCE_CHANNEL, onMessage -> {
            this.execute();
        });
    }

    public Future<Void> start() {
        this.start = true;
        final Future<Void> future = execute();
        return future;
    }

    public void stop() {
        this.start = false;
    }

    @Override
    public boolean isStarted() {
        return start;
    }

    @Override
    public void setOnEnd(final Handler<AsyncResult<ResourceLoaderResult>> handler) {
        this.onEnd = handler;
    }

    public Future<Void> execute(boolean force) {
        //TODO set a max attempt?
        //TODO debounce ms? (config)
        //TODO create a job to archive this table on night?
        //TODO optimize and merge updating related to one resource?
        // lock running
        if (!this.start && !force) {
            return Future.failedFuture("resource loader is stopped");
        }
        return running.compose(onReady -> {
            running = Future.future();
            // use modulo to split between different thread
            final String modulo = this.modulo == 1 ? "" : String.format(" AND MOD(id,%s)=0 ", this.modulo);
            final String query = String.format("SELECT * FROM explorer.resource_queue WHERE attempt_status=$1 %s ORDER BY priority DESC, created_at ASC LIMIT $2", modulo);
            this.pgClient.preparedQuery(query, Tuple.of(STATUS_PENDING, bulkSize)).compose(result -> {
                return loadPendings(result);
            }).compose(bulkResults -> {
                return saveStatus(bulkResults);
            }).setHandler(e -> {
                try{
                    if (e.succeeded()) {
                        this.onEnd.handle(new DefaultAsyncResult<>(e.result()));
                    } else {
                        this.onEnd.handle(new DefaultAsyncResult<>(e.cause()));
                        log.error("Failed loading resources to engine:", e.cause());
                    }
                }catch(Exception exc){}
                running.complete();
            });
            return running;
        });
    }

    protected Future<List<JsonObject>> loadPendings(final PgRowSet result) {
        final List<ResourceService.ResourceBulkOperation<Long>> resources = new ArrayList<>();
        for (final Row row : result) {
            final String resourceAction = row.getString("resource_action");
            final Long idQueue = row.getLong("id");
            final String idResource = row.getString("id_resource");
            final JsonObject json = (JsonObject) (row.getJson("payload")).value();
            final String creatorId = json.getString("creatorId");
            json.put("_id", idResource);
            final ResourceService.ResourceBulkOperationType type = ResourceService.getOperationType(resourceAction);
            resources.add(new ResourceService.ResourceBulkOperation(json, type, idQueue));
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
                final List<Long> ids = succeed.stream().map(e -> e.getLong(ResourceService.CUSTOM_IDENTIFIER)).collect(Collectors.toList());
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(STATUS_SUCCESS), ids);
                final String placeholder = PostgresClient.inPlaceholder(succeed, 2);
                final String query = String.format("UPDATE explorer.resource_queue SET  attempt_status=$1, attempted_count=attempted_count+1, attempted_at=NOW() WHERE id IN (%s)", placeholder);
                transaction.addPreparedQuery(query, tuple);
            }
            if (failed.size() > 0) {
                final LocalDateTime now = LocalDateTime.now();
                final Map<String, Object> defaultValues = new HashMap<>();
                defaultValues.put("_attemptat", now);
                final List<Long> ids = failed.stream().map(e -> e.getLong(ResourceService.CUSTOM_IDENTIFIER)).collect(Collectors.toList());
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(now), ids);
                final String placeholder = PostgresClient.inPlaceholder(failed, 2);
                final String query = String.format("UPDATE explorer.resource_queue SET  attempted_count=attempted_count+1, attempted_at=? WHERE id IN (%s)", placeholder);
                transaction.addPreparedQuery(query, tuple);
                final Tuple tupleMessage = PostgresClient.insertValues(failed, Tuple.tuple(), defaultValues, "_id", FolderService.ERROR_FIELD, "_attemptat");
                final String placeholderMessage = PostgresClient.insertPlaceholders(failed, 1, ResourceService.CUSTOM_IDENTIFIER, "id_resource", FolderService.ERROR_FIELD, "_attemptat");
                final String queryMessage = String.format("INSERT INTO explorer.resource_queue_causes (id, id_resource, attempt_reason, attempted_at) VALUES %s", placeholderMessage);
                transaction.addPreparedQuery(queryMessage, tupleMessage);
            }
            return transaction.commit().map(e -> new ResourceLoaderResult(succeed, failed));
        });
    }
}
