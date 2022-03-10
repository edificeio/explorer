package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.redis.RedisClient;

import java.util.List;
import java.util.function.Function;

public interface MessageReader {

    static MessageReader redis(final RedisClient client, final JsonObject config) {
        return new MessageReaderRedis(client, config);
    }

    static MessageReader postgres(final PostgresClient client, final JsonObject config) {
        return new MessageReaderPostgres(client, config);
    }

    void stop();

    Future<Void> start();

    MessageReaderStatus getStatus();

    default boolean isStopped() {
        return !isRunning();
    }

    default boolean isRunning() {
        return MessageReaderStatus.Running.equals(getStatus());
    }

    Function<Void, Void> listenNewMessages(final Handler<Void> handler);

    Future<List<ExplorerMessageForIngest>> getIncomingMessages(final int maxBatchSize);

    Future<List<ExplorerMessageForIngest>> getFailedMessages(final int maxBatchSize, final int maxAttempt);

    Future<Void> updateStatus(final IngestJob.IngestJobResult ingestResult, final int maxAttempt);

    Future<JsonObject> getMetrics();

    enum MessageReaderStatus {
        Stopped, Running
    }

    interface MessageReaderListener {
        boolean canBeNotified();

        void notifyMessage();
    }
}
