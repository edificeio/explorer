package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorder;
import com.opendigitaleducation.explorer.ingest.MergeMessagesResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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

    public MicrometerJobMetricsRecorder(final Configuration configuration) {
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
        final Timer.Builder ingestionTimesBuilder = Timer.builder("ingest.ingestion.time")
                .description("ingestion time of messages");
        if(configuration.sla.isEmpty()) {
            ingestionTimesBuilder
                    .publishPercentileHistogram()
                    .maximumExpectedValue(Duration.ofMinutes(2L));
        } else {
            ingestionTimesBuilder.sla(configuration.sla.toArray(new Duration[0]));
        }
        ingestionTimes = ingestionTimesBuilder.register(registry);
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

    public static class Configuration {
        private final List<Duration> sla;

        public Configuration(List<Duration> sla) {
            this.sla = sla;
        }

        /**
         * <p>Creates the configuration of the metrics recorder based on the global configuration file.</p>
         * <p>
         *     It expects that the configuration contains a property <strong>metrics</strong> that contains the
         *     following fields :
         *     <ul>
         *         <li>sla, the desired buckets (in milliseconds) for the time of ingestion of messages</li>
         *     </ul>
         * </p>
         * @param conf
         * @return
         */
        public static Configuration fromJson(final JsonObject conf) {
            final List<Duration> sla;
            if(conf == null || !conf.containsKey("metrics")) {
                sla = Collections.emptyList();
            } else {
                final JsonObject metrics = conf.getJsonObject("metrics");
                sla = metrics.getJsonArray("sla", new JsonArray()).stream()
                        .mapToInt(slaBucket -> (int)slaBucket)
                        .sorted()
                        .mapToObj(Duration::ofMillis)
                        .collect(Collectors.toList());
            }
            return new Configuration(sla);
        }
    }

}
