package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorder;
import com.opendigitaleducation.explorer.ingest.MergeMessagesResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Records metrics via micrometer.
 */
public class MicrometerJobMetricsRecorder implements IngestJobMetricsRecorder {
    private final Counter ingestCycleStartedCounter;
    private final Counter ingestCycleSucceededCounter;
    private final Counter ingestCycleFailedCounter;
    private final Counter ingestCycleCompletedCounter;
    private final Counter ingestCyclePendingCounter;
    private final Counter failedMessagesCounter;
    private final Counter succeededMessagesCounter;
    private final Counter messagesAttemptedTooManyTimesCounter;
    private final Counter jobCounter;
    private final Timer ingestionTimes;

    public MicrometerJobMetricsRecorder() {
        final MeterRegistry registry = BackendRegistries.getDefaultNow();
        if(registry == null) {
            throw new IllegalStateException("micrometer.registries.empty");
        }
        ingestCycleStartedCounter = Counter.builder("ingest.cycle.started")
                .description("number of ingest cycles that were started")
                .register(registry);
        ingestCycleSucceededCounter = Counter.builder("ingest.cycle.succeeded")
                .description("number of ingest cycles that were started")
                .register(registry);
        ingestCycleFailedCounter = Counter.builder("ingest.cycle.failed")
                .description("number of ingest cycles that were started")
                .register(registry);
        ingestCycleCompletedCounter = Counter.builder("ingest.cycle.completed")
                .description("number of ingest cycles that were started")
                .register(registry);
        failedMessagesCounter = Counter.builder("ingest.message.failed")
                .description("number of failed ingest messages")
                .register(registry);
        succeededMessagesCounter = Counter.builder("ingest.message.succeeded")
                .description("number of succeeded messages")
                .register(registry);
        jobCounter = Counter.builder("ingest.job")
                .description("number of launched jobs")
                .register(registry);
        ingestionTimes = Timer.builder("ingest.ingestion.time")
                .description("ingestion time of messages")
                .maximumExpectedValue(Duration.ofMinutes(2L))
                .publishPercentileHistogram()
                .register(registry);
        ingestCyclePendingCounter = Counter.builder("ingest.cycle.pending")
                .description("number of pending cycles")
                .register(registry);
        messagesAttemptedTooManyTimesCounter = Counter.builder("ingest.message.too.much.attempts")
                .description("number of messages that were attempted too many times")
                .register(registry);
    }

    @Override
    public void onJobStarted() {
        jobCounter.increment();
    }

    @Override
    public void onJobStopped() {
        jobCounter.increment(-1.);
    }

    @Override
    public void onIngestCycleStarted() {
        ingestCycleStartedCounter.increment();
    }

    @Override
    public void onIngestCycleSucceeded() {
        ingestCycleSucceededCounter.increment();

    }

    @Override
    public void onIngestCycleFailed() {
        ingestCycleFailedCounter.increment();
    }

    @Override
    public void onIngestCycleCompleted() {
        ingestCycleCompletedCounter.increment();
    }

    @Override
    public void onIngestCycleResult(IngestJob.IngestJobResult ingestJobResult, MergeMessagesResult mergeResult) {
        final long now = System.currentTimeMillis();
        failedMessagesCounter.increment(ingestJobResult.getFailed().size());
        succeededMessagesCounter.increment(ingestJobResult.getSucceed().size());
        for (ExplorerMessageForIngest explorerMessageForIngest : ingestJobResult.getSucceed()) {
            getMessageCreationTime(explorerMessageForIngest).ifPresent(creationTime -> ingestionTimes.record(now - creationTime, TimeUnit.MILLISECONDS));
        }
    }

    private Optional<Long> getMessageCreationTime(final ExplorerMessageForIngest explorerMessageForIngest) {
        final JsonObject message = explorerMessageForIngest.getMessage();
        return Stream.of("createdAt", "deletedAt")
            .map(message::getLong)
            .findAny();
    }

    @Override
    public void onPendingIngestCycleExecutionChanged() {
        ingestCyclePendingCounter.increment();
    }

    @Override
    public void onMessagesAttempedTooManyTimes(int nbMessagesAttemptedTooManyTimes) {
        messagesAttemptedTooManyTimesCounter.increment(nbMessagesAttemptedTooManyTimes);
    }

}
