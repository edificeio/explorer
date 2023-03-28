package com.opendigitaleducation.explorer.plugin;

import com.opendigitaleducation.explorer.redis.RedisClient;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.function.Function;

public class ExplorerPluginCommunicationRedis implements ExplorerPluginCommunication {
    public static final JsonArray DEFAULT_STREAMS = new JsonArray().add("explorer_high").add("explorer_medium").add("explorer_low");
    static Logger log = LoggerFactory.getLogger(ExplorerPluginCommunicationRedis.class);
    static Map<ExplorerMessage.ExplorerPriority, String> STREAMS = new HashMap<>();

    static {
        STREAMS.put(ExplorerMessage.ExplorerPriority.Low, "explorer_low");
        STREAMS.put(ExplorerMessage.ExplorerPriority.Medium, "explorer_medium");
        STREAMS.put(ExplorerMessage.ExplorerPriority.High, "explorer_high");
    }

    private final RedisClient redisClient;
    private final List<RedisExplorerFailed> pendingFailed = new ArrayList<>();
    private final Vertx vertx;
    private final int retryUntil = 30000;

    public ExplorerPluginCommunicationRedis(final Vertx vertx, final RedisClient redisClient) {
        this.redisClient = redisClient;
        this.vertx = vertx;
    }

    @Override
    public Future<Void> pushMessage(final ExplorerMessage message) {
        return pushMessage(Arrays.asList(message));
    }

    @Override
    public Future<Void> pushMessage(final List<ExplorerMessage> messages) {
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

    @Override
    public Vertx vertx() {
        return vertx;
    }

    protected JsonObject toRedisJson(final ExplorerMessage message) {
        final JsonObject json = new JsonObject();
        json.put("resource_action", message.getAction());
        json.put("id_resource", message.getId());
        json.put("payload", message.getMessage().encode());
        return json;
    }

    protected Map<String, List<JsonObject>> toRedisMap(final List<ExplorerMessage> messages) {
        final Map<String, List<JsonObject>> map = new HashMap<>();
        for (final ExplorerMessage m : messages) {
            final String stream = STREAMS.get(m.getPriority());
            map.putIfAbsent(stream, new ArrayList<>());
            map.get(stream).add(toRedisJson(m));
        }
        return map;
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
