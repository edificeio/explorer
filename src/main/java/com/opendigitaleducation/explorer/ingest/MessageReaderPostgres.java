package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.plugin.ExplorerPluginCommunicationPostgres;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientChannel;
import io.reactiverse.pgclient.PgRowSet;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MessageReaderPostgres implements MessageReader {
    static final Logger log = LoggerFactory.getLogger(MessageReaderPostgres.class);
    static final int DEFAULT_JOB_MODULO = 1;
    static final int STATUS_SUCCESS = 1;
    static final int STATUS_FAIL = -1;
    static final int STATUS_PENDING = 0;
    private final int modulo;
    private final PostgresClientChannel pgClient;
    private final List<Handler<Void>> listeners = new ArrayList<>();
    private final JsonObject metrics = new JsonObject();
    private int pendingNotifications = 0;
    private MessageReaderStatus status = MessageReaderStatus.Running;

    public MessageReaderPostgres(final PostgresClient postgresClient, final JsonObject config) {
        this.pgClient = postgresClient.getClientChannel();
        this.modulo = config.getInteger("consumer-modulo", DEFAULT_JOB_MODULO);
        //TODO close remove listeners?
        this.pgClient.listen(ExplorerPluginCommunicationPostgres.RESOURCE_CHANNEL, onMessage -> {
            this.pendingNotifications++;
            //do not notify each time
            if (isStopped()) {
                metrics.put("last_listen_skip_at", new Date().getTime());
                metrics.put("listen_skip_count", metrics.getInteger("listen_skip_count", 0) + 1);
            } else {
                metrics.put("last_listen_at", new Date().getTime());
                metrics.put("listen_count", metrics.getInteger("listen_count", 0) + 1);
            }
            //call listeners (avoid concurrent modification
            notifyListeners();
        });
    }

    @Override
    public Function<Void, Void> listenNewMessages(final Handler<Void> handler) {
        listeners.add(handler);
        //function to cancel subscription
        return e -> {
            listeners.remove(handler);
            return null;
        };
    }

    @Override
    public Future<List<ExplorerMessageForIngest>> getIncomingMessages(final int maxBatchSize) {
        final String attemptFilter = " AND attempted_count = 0 ";
        return fetch(maxBatchSize, Optional.empty(), attemptFilter);
    }


    @Override
    public Future<List<ExplorerMessageForIngest>> getFailedMessages(final int maxBatchSize, final int maxAttempt) {
        final String attemptFilter = String.format(" AND attempted_count > 0 AND attempted_count <= %s ", maxAttempt);
        return fetch(maxBatchSize, Optional.of(maxAttempt), attemptFilter);
    }

    @Override
    public MessageReaderStatus getStatus() {
        return status;
    }

    @Override
    public void stop() {
        this.status = MessageReaderStatus.Stopped;
    }

    @Override
    public Future<Void> start() {
        notifyListeners();
        this.status = MessageReaderStatus.Running;
        return Future.succeededFuture();
    }

    protected void notifyListeners() {
        if (this.pendingNotifications > 0 && isRunning()) {
            final List<Handler<Void>> copy = new ArrayList<>(listeners);
            for (final Handler<Void> listener : copy) {
                listener.handle(null);
            }
        }
        this.pendingNotifications = 0;
    }

    Future<List<ExplorerMessageForIngest>> fetch(final int maxBatchSize, final Optional<Integer> maxAttempt, final String attemptFilter) {
        // use modulo to split between different thread
        final String modulo = this.modulo == 1 ? "" : String.format(" AND MOD(id,%s)=0 ", this.modulo);
        final String query = String.format("SELECT * FROM explorer.resource_queue WHERE attempt_status=$1 %s %s ORDER BY priority DESC, created_at ASC LIMIT $2", modulo, attemptFilter);
        return this.pgClient.preparedQuery(query, Tuple.of(STATUS_PENDING, maxBatchSize)).map(result -> {
            final List<ExplorerMessageForIngest> all = new ArrayList<>();
            for (final Row row : result) {
                final String resourceAction = row.getString("resource_action");
                final Long idQueue = row.getLong("id");
                final String idResource = row.getString("id_resource");
                final JsonObject json = (JsonObject) (row.getJson("payload")).value();
                final ExplorerMessageForIngest message = new ExplorerMessageForIngest(resourceAction, idQueue + "", idResource, json);
                all.add(message);
            }
            //metrics
            if (maxAttempt.isPresent()) {
                metrics.put("last_fetch_max_attempt", maxAttempt.get());
            }
            metrics.put("last_fetch_max_batch_size", maxBatchSize);
            metrics.put("last_fetch_at", new Date().getTime());
            metrics.put("last_fetch_size", all.size());
            metrics.put("fetch_count", metrics.getInteger("fetch_count", 0) + 1);
            //return
            return all;
        });
    }

    @Override
    public Future<Void> updateStatus(final IngestJob.IngestJobResult result, final int maxAttempt) {
        final List<ExplorerMessageForIngest> succeed = result.succeed;
        final List<ExplorerMessageForIngest> failed = result.failed;
        final List<JsonObject> failedJson = result.failed.stream().filter(e->{
            return e.getIdQueue().isPresent();
        }).map(e -> {
            return e.getMessage()
                    .put("_idQueue", Long.valueOf(e.getIdQueue().get()))
                    .put("_idResource", e.getId()).put("_error", e.getError());
        }).collect(Collectors.toList());
        //save
        return this.pgClient.transaction().compose(transaction -> {
            if (succeed.size() > 0) {
                final List<Long> ids = succeed.stream().filter(e->{
                    return e.getIdQueue().isPresent();
                }).map(e -> Long.valueOf(e.getIdQueue().get())).collect(Collectors.toList());
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(STATUS_SUCCESS), ids);
                final String placeholder = PostgresClient.inPlaceholder(succeed, 2);
                final String query = String.format("UPDATE explorer.resource_queue SET  attempt_status=$1, attempted_count=attempted_count+1, attempted_at=NOW() WHERE id IN (%s)", placeholder);
                transaction.addPreparedQuery(query, tuple);
            }
            if (failed.size() > 0) {
                final LocalDateTime now = LocalDateTime.now();
                final Map<String, Object> defaultValues = new HashMap<>();
                defaultValues.put("_attemptat", now);
                defaultValues.put("_error", "");
                final List<Long> ids = failed.stream().filter(e->{
                    return e.getIdQueue().isPresent();
                }).map(e -> Long.valueOf(e.getIdQueue().get())).collect(Collectors.toList());
                final Tuple tuple = PostgresClient.inTuple(Tuple.of(now), ids);
                final String placeholder = PostgresClient.inPlaceholder(failed, 2);
                final String query = String.format("UPDATE explorer.resource_queue SET  attempted_count=attempted_count+1, attempted_at=$1 WHERE id IN (%s)", placeholder);
                transaction.addPreparedQuery(query, tuple);
                final Tuple tupleMessage = PostgresClient.insertValues(failedJson, Tuple.tuple(), defaultValues, "_idQueue", "_idResource", "_error", "_attemptat");
                final String placeholderMessage = PostgresClient.insertPlaceholders(failedJson, 1, "_idQueue", "id_resource", "_error", "_attemptat");
                final String queryMessage = String.format("INSERT INTO explorer.resource_queue_causes (id, id_resource, attempt_reason, attempted_at) VALUES %s", placeholderMessage);
                transaction.addPreparedQuery(queryMessage, tupleMessage);
            }
            return transaction.commit().onFailure(e -> {
                log.error("Could not update resource status on queue: ", e);
            }).mapEmpty();
        });
    }

    @Override
    public Future<JsonObject> getMetrics() {
        final Integer lastMaxAtempt = metrics.getInteger("last_fetch_max_attempt", -1);
        final JsonObject metrics = this.metrics.copy();
        final Future<PgRowSet> f1 = pgClient.preparedQuery("SELECT COUNT(*) as nb, MIN(created_at) mindate, MAX(created_at) maxdate FROM explorer.resource_queue WHERE attempted_count = 0", Tuple.tuple()).onSuccess(result -> {
            for (final Row row : result) {
                metrics.put("pending_count", row.getValue("nb"));
                metrics.put("pending_min", row.getValue("mindate"));
                metrics.put("pending_max", row.getValue("maxdate"));
            }
        });
        final Future<PgRowSet> f2 = pgClient.preparedQuery("SELECT COUNT(*) as nb, MIN(created_at) mindate, MAX(created_at) maxdate FROM explorer.resource_queue WHERE attempted_count < $1", Tuple.of(lastMaxAtempt)).onSuccess(result -> {
            for (final Row row : result) {
                metrics.put("pending_retry_count", row.getValue("nb"));
                metrics.put("pending_retry_min", row.getValue("mindate"));
                metrics.put("pending_retry_max", row.getValue("maxdate"));
            }
        });
        final Future<PgRowSet> f3 = pgClient.preparedQuery("SELECT COUNT(*) as nb, MIN(created_at) mindate, MAX(created_at) maxdate FROM explorer.resource_queue WHERE attempted_count > $1", Tuple.of(lastMaxAtempt)).onSuccess(result -> {
            for (final Row row : result) {
                metrics.put("pending_failed_count", row.getValue("nb"));
                metrics.put("pending_failed_min", row.getValue("mindate"));
                metrics.put("pending_failed_max", row.getValue("maxdate"));
            }
        });
        return CompositeFuture.all(f1, f2, f3).map(e -> {
            return metrics;
        });
    }
}
