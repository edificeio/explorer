package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.redis.RedisClient;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.function.Function;

public interface MessageReader {

    void stop();

    Future<Void> start();

    MessageReaderStatus getStatus();

    default boolean isStopped(){ return !isRunning(); }

    default boolean isRunning(){ return MessageReaderStatus.Running.equals(getStatus()); }

    Function<Void, Void> listenNewMessages(final Handler<Void> handler);

    Future<List<MessageIngester.Message>> getIncomingMessages(final int maxBatchSize);

    Future<List<MessageIngester.Message>> getFailedMessages(final int maxBatchSize, final int maxAttempt);

    Future<Void> updateStatus(final IngestJob.IngestJobResult ingestResult, final int maxAttempt);

    Future<JsonObject> getMetrics();

    static MessageReader redis(final RedisClient client, final JsonObject config){
        return new MessageReaderRedis(client, config);
    }

    static MessageReader postgres(final PostgresClient client, final JsonObject config){
        return new MessageReaderPostgres(client, config);
    }

    interface MessageReaderListener{
        boolean canBeNotified();
        void notifyMessage();
    }

    enum MessageReaderStatus{
        Stopped, Running;
    }
}
