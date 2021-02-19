package com.opendigitaleducation.explorer.postgres;

import io.reactiverse.pgclient.*;
import io.reactiverse.pgclient.pubsub.PgSubscriber;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//TODO merge with entcore common
public class PostgresClient {
    private final Vertx vertx;
    private final JsonObject config;

    public PostgresClient(final Vertx vertx, final JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public static String insertPlaceholders(final List<JsonObject> rows, final int startAt, final String... column) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        for (final JsonObject row : rows) {
            final List<String> group = new ArrayList<>();
            for (final String col : column) {
                group.add("$" + placeholderCounter);
                placeholderCounter++;
            }
            placeholders.add(String.format("(%s)", String.join(",", group)));
        }
        return String.join(",", placeholders);
    }

    public static String insertPlaceholdersFromMap(final List<Map<String,Object>> rows, final int startAt, final String... column) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        for (final Map<String,Object> row : rows) {
            final List<String> group = new ArrayList<>();
            for (final String col : column) {
                group.add("$" + placeholderCounter);
                placeholderCounter++;
            }
            placeholders.add(String.format("(%s)", String.join(",", group)));
        }
        return String.join(",", placeholders);
    }

    public static Tuple insertValues(final List<JsonObject> rows, final Tuple tuple, final String... column) {
        for (final JsonObject row : rows) {
            for (final String col : column) {
                tuple.addValue(row.getValue(col));
            }
        }
        return tuple;
    }

    public static Tuple insertValuesFromMap(final List<Map<String, Object>> rows, final Tuple tuple, final String... column) {
        for (final Map<String, Object> row : rows) {
            for (final String col : column) {
                tuple.addValue(row.get(col));
            }
        }
        return tuple;
    }

    public static Tuple insertValues(final List<JsonObject> rows, final Tuple tuple, final Map<String, Object> defaultValues, final String... column) {
        for (final JsonObject row : rows) {
            for (final String col : column) {
                tuple.addValue(row.getValue(col, defaultValues.get(col)));
            }
        }
        return tuple;
    }

    public static <T> String inPlaceholder(final List<T> values, int startAt) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        for (final T value : values) {
            placeholders.add("$" + placeholderCounter);
            placeholderCounter++;
        }
        return String.join(",", placeholders);
    }

    public static <T> Tuple inTuple(final Tuple tuple, final List<T> values) {
        for (final T value : values) {
            tuple.addValue(value);
        }
        return tuple;
    }

    public PostgresClientChannel getClientChannel() {
        final PgSubscriber pgSubscriber = PgSubscriber.subscriber(vertx, new PgConnectOptions()
                .setPort(config.getInteger("port", 5432))
                .setHost(config.getString("host"))
                .setDatabase(config.getString("database"))
                .setUser(config.getString("user"))
                .setPassword(config.getString("password"))
        );
        return new PostgresClientChannel(pgSubscriber, config);
    }

    public PostgresClientPool getClientPool() {
        final PgPool pgPool = PgClient.pool(vertx, new PgPoolOptions()
                .setPort(config.getInteger("port", 5432))
                .setHost(config.getString("host"))
                .setDatabase(config.getString("database"))
                .setUser(config.getString("user"))
                .setPassword(config.getString("password"))
                .setMaxSize(config.getInteger("pool-size", 10))
        );
        return new PostgresClientPool(pgPool, config);
    }

    public static class PostgresTransaction {
        private static final Logger log = LoggerFactory.getLogger(PostgresClientPool.class);
        private final PgTransaction pgTransaction;
        private final List<Future> futures = new ArrayList<>();

        PostgresTransaction(final PgTransaction pgTransaction) {
            this.pgTransaction = pgTransaction;
        }

        public Future<PgRowSet> addPreparedQuery(String query, Tuple tuple) {
            final Future<PgRowSet> future = Future.future();
            this.pgTransaction.preparedQuery(query, tuple, future.completer());
            futures.add(future);
            return future;
        }

        public Future<Void> notify(final String channel, final String message) {
            final Future<Void> future = Future.future();
            //prepareQuery not works with notify allow only internal safe message
            this.pgTransaction.query(
                    "NOTIFY " + channel + ", '" + message + "'", notified -> {
                        future.handle(notified.mapEmpty());
                        if (notified.failed()) {
                            log.error("Could not notify channel: " + channel);
                        }
                    });
            futures.add(future);
            return future;
        }

        public Future<Void> commit() {
            return CompositeFuture.all(futures).compose(r -> {
                final Future<Void> future = Future.future();
                this.pgTransaction.commit(future.completer());
                return future;
            });
        }

        public Future<Void> rollback() {
            final Future<Void> future = Future.future();
            this.pgTransaction.rollback(future.completer());
            return future;
        }
    }
}
