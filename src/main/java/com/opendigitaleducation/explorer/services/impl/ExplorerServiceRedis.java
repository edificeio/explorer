package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.redis.RedisClient;
import com.opendigitaleducation.explorer.services.ExplorerService;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

public class ExplorerServiceRedis implements ExplorerService {
    public static final JsonArray DEFAULT_STREAMS = new JsonArray().add("explorer_high").add("explorer_medium").add("explorer_low");
    static Logger log = LoggerFactory.getLogger(ExplorerServiceRedis.class);
    static Map<Integer, String> STREAMS = new HashMap<>();

    static {
        STREAMS.put(ExplorerService.PRIORITY_LOW, "explorer_low");
        STREAMS.put(ExplorerService.PRIORITY_DEFAULT, "explorer_medium");
        STREAMS.put(ExplorerService.PRIORITY_HIGH, "explorer_high");
    }

    private final RedisClient redisClient;
    private final List<ExplorerServiceRedis.RedisExplorerFailed> pendingFailed = new ArrayList<>();
    private final Vertx vertx;
    private final int retryUntil = 30000;

    public ExplorerServiceRedis(final Vertx vertx, final RedisClient redisClient) {
        this.redisClient = redisClient;
        this.vertx = vertx;
    }

    protected JsonObject toRedisJson(final ExplorerMessageBuilder message) {
        final JsonObject json = new JsonObject();
        json.put("resource_action", message.getAction());
        json.put("id_resource", message.getId());
        json.put("payload", message.getMessage());
        return json;
    }

    protected Map<String, List<JsonObject>> toRedisMap(final List<ExplorerMessageBuilder> message) {
        final Map<String, List<JsonObject>> map = new HashMap<>();
        for (final ExplorerMessageBuilder m : message) {
            final String stream = STREAMS.get(m.getPriority());
            map.putIfAbsent(stream, new ArrayList<>());
            map.get(stream).add(toRedisJson(m));
        }
        return map;
    }

    @Override
    public Future<Void> push(final ExplorerMessageBuilder message) {
        //TODO debounce ?
        return push(Arrays.asList(message));
    }

    @Override
    public Future<Void> push(List<ExplorerMessageBuilder> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture();
        }
        final List<Future> futures = new ArrayList<>();
        final Map<String, List<JsonObject>> map = toRedisMap(messages);
        for (final String stream : map.keySet()) {
            futures.add(redisClient.xAdd(stream, map.get(stream)).onFailure(e -> {
                //TODO push somewhere else to retry? limit in size? in time? fallback to redis?
                final RedisExplorerFailed fail = new RedisExplorerFailed(stream, map.get(stream));
                pendingFailed.add(fail);
                vertx.setTimer(retryUntil, rr -> {
                    pendingFailed.remove(fail);
                });
                log.error("Failed to push resources to stream " + stream, e.getCause());
            }));
        }
        return CompositeFuture.all(futures).mapEmpty();
    }

    class RedisExplorerFailed {
        final String stream;
        final List<JsonObject> jsons;

        public RedisExplorerFailed(final String stream, final List<JsonObject> jsons) {
            this.stream = stream;
            this.jsons = jsons;
        }
    }
}
