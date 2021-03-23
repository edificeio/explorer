package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientPool;
import com.opendigitaleducation.explorer.services.ExplorerService;
import io.reactiverse.pgclient.Tuple;
import io.reactiverse.pgclient.data.Json;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ExplorerServicePostgres implements ExplorerService {
    static Logger log = LoggerFactory.getLogger(ExplorerServicePostgres.class);
    private final PostgresClientPool pgPool;
    private final List<PostgresExplorerFailed> pendingFailed = new ArrayList<>();
    private final Vertx vertx;
    private final int retryUntil = 30000;

    public ExplorerServicePostgres(final Vertx vertx, final PostgresClient pgClient) {
        this.pgPool = pgClient.getClientPool();
        this.vertx = vertx;
    }

    @Override
    public Future<Void> push(final ExplorerMessageBuilder message) {
        //TODO debounce ?
        //TODO create a job to archive this table on night?
        return push(Arrays.asList(message));
    }

    @Override
    public Future<Void> push(List<ExplorerMessageBuilder> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture();
        }
        return pgPool.transaction().compose(transaction -> {
            final LocalDateTime now = LocalDateTime.now();
            final List<Map<String, Object>> rows = messages.stream().map(e -> {
                final Map<String, Object> map = new HashMap<>();
                map.put("id_resource", e.getId());
                map.put("created_at", now);
                map.put("resource_action", e.getAction());
                map.put("payload", Json.create(e.getMessage()));
                map.put("priority", e.getPriority());
                return map;
            }).collect(Collectors.toList());
            final String placeholder = PostgresClient.insertPlaceholdersFromMap(rows, 1, "id_resource", "created_at", "resource_action", "payload", "priority");
            final Tuple values = PostgresClient.insertValuesFromMap(rows, Tuple.tuple(), "id_resource", "created_at", "resource_action", "payload", "priority");
            //TODO dynamic table name?
            final String query = String.format("INSERT INTO explorer.resource_queue (id_resource,created_at, resource_action, payload, priority) VALUES %s", placeholder);
            transaction.addPreparedQuery(query, values).onComplete(r -> {
                if (r.failed()) {
                    //TODO push somewhere else to retry? limit in size? in time? fallback to redis?
                    final PostgresExplorerFailed fail = new PostgresExplorerFailed(query, values);
                    pendingFailed.add(fail);
                    vertx.setTimer(retryUntil, rr -> {
                        pendingFailed.remove(fail);
                    });
                    log.error("Failed to push resources to queue: ", r.cause());
                    log.error("Query causing error: " + query);
                }
            });
            //retry failed
            for (final PostgresExplorerFailed failed : pendingFailed) {
                transaction.addPreparedQuery(failed.query, failed.tuple).onComplete(r -> {
                    if (r.succeeded()) {
                        pendingFailed.remove(failed);
                    }
                });
            }
            //
            transaction.notify(ExplorerService.RESOURCE_CHANNEL, "new_resources").onComplete(e -> {
                if (e.failed()) {
                    log.error("Failed to notify new ressources: ", e.cause());
                }
            });
            final Promise<Void> future = Promise.promise();
            transaction.commit().onComplete(e -> {
                future.handle(e);
                if (e.failed()) {
                    log.error("Failed to commit resources to queue: ", e.cause());
                }
            });
            return future.future();
        });
    }

    class PostgresExplorerFailed {
        final String query;
        final Tuple tuple;

        public PostgresExplorerFailed(String query, Tuple tuple) {
            this.query = query;
            this.tuple = tuple;
        }
    }
}
