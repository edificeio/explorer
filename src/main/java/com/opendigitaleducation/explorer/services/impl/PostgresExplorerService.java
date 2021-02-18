package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.postgres.PostgresClientPool;
import com.opendigitaleducation.explorer.services.ExplorerService;
import io.reactiverse.pgclient.Tuple;
import io.reactiverse.pgclient.data.Json;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PostgresExplorerService implements ExplorerService {
    static final int MAX_RANDOM = 10000;
    static Logger log = LoggerFactory.getLogger(PostgresExplorerService.class);
    private final PostgresClientPool pgPool;
    private final Random rnd = new Random();
    private final List<Tuple> pendingFailed = new ArrayList<>();


    public PostgresExplorerService(final PostgresClient pgClient) {
        this.pgPool = pgClient.getClientPool();
    }

    @Override
    public Future<Void> push(final ExplorerMessageBuilder message) {
        //TODO debounce ?
        return pgPool.transaction().compose(transaction -> {
            final JsonObject json = message.getMessage();
            //TODO dynamic table name?
            final String query = "INSERT INTO explorer.resource_queue (id,created_at, resource_action, payload, priority, random_num) VALUES ($1,$2,$3,$4,$5,$6)";
            final Tuple tuple = Tuple.of(message.getId(), LocalDateTime.now(), message.getAction(), Json.create(json), message.getPriority(), rnd.nextInt(MAX_RANDOM));
            transaction.addPreparedQuery(query, tuple).setHandler(r -> {
                if (r.failed()) {
                    //TODO push somewhere else to retry? limit in size? in time?
                    pendingFailed.add(tuple);
                    log.error("Failed to push resources to queue: ", r.cause());
                }
            });
            //retry failed
            for (final Tuple tupleFailed : pendingFailed) {
                transaction.addPreparedQuery(query, tupleFailed).setHandler(r -> {
                    if (r.succeeded()) {
                        pendingFailed.remove(tupleFailed);
                    }
                });
            }
            //
            transaction.notify(ExplorerService.RESOURCE_CHANNEL, "new_events");
            final Future<Void> future = Future.future();
            transaction.commit().setHandler(e -> {
                future.handle(e);
                if (e.failed()) {
                    log.error("Failed to commit resources to queue: ", e.cause());
                }
            });
            return future;
        });
    }
}
