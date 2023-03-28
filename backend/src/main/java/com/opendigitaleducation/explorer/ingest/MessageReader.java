package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.ExplorerPluginFactory;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.redis.RedisClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface MessageReader {


    static MessageReader create(final Vertx vertx, final JsonObject config, final JsonObject ingestConfig) throws Exception {
        ExplorerPluginFactory.init(vertx, config);
        if(config.getString("stream", "redis").equalsIgnoreCase("redis")){
            final RedisClient redis = RedisClient.create(vertx, ExplorerPluginFactory.getRedisConfig());
            return redis(redis, ingestConfig);
        }else{
            final IPostgresClient postgres = IPostgresClient.create(vertx, ExplorerPluginFactory.getPostgresConfig(), true, false);
            return postgres(postgres, ingestConfig);
        }
    }

    static MessageReader redis(final RedisClient client, final JsonObject config) {
        return new MessageReaderRedis(client, config);
    }

    static MessageReader postgres(final IPostgresClient client, final JsonObject config) {
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

    /**
     * Fetch messages to treat by picking them from the following streams in the
     * following order :
     * <ol>
     *     <li>messages in error</li>
     *     <li>messages which where not acknowledged</li>
     *     <li>new messages</li>
     * </ol>
     * <u>NB: </u> The order is important ! Messages should be treated in the order they are presented.
     * @param maxBatchSize Maximum number of batches
     * @param maxAttempt Maximum attempts
     * @return The list of the messages to treat
     */
    default Future<List<ExplorerMessageForIngest>> getMessagesToTreat(final int maxBatchSize, final int maxAttempt) {
        return getFailedMessages(maxBatchSize, maxAttempt).flatMap(failedMessages -> {
            final int nbMessagesLeftToFetch = maxBatchSize - failedMessages.size();
            return getIncomingMessages(nbMessagesLeftToFetch).map(incominMessages -> {
                final List<ExplorerMessageForIngest> allMessages = new ArrayList<>(failedMessages);
                allMessages.addAll(incominMessages);
                return allMessages;
            });
        });
    }

    Future<List<ExplorerMessageForIngest>> getIncomingMessages(final int maxBatchSize);

    Future<List<ExplorerMessageForIngest>> getFailedMessages(final int maxBatchSize, final int maxAttempt);

    /**
     * Updates in the message buffer the status of messages that have been ingested.
     * @param ingestResult Result of the ingestion
     * @param maxAttempt Maximum attempt of ingestion
     * @return
     */
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
