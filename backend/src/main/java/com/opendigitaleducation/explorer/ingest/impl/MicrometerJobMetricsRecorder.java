package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorder;
import com.opendigitaleducation.explorer.ingest.MergeMessagesResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

import java.time.Duration;
import java.util.ArrayList;
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
    private final Counter ingestCycleWithFailuresCounter;
    private final Counter succeededMessagesCounter;
    private final Counter messagesAttemptedTooManyTimesCounter;
    private final Counter jobCounter;
    private final Timer ingestionCycleTimes;
    private final Timer ingestionTimes;
    private final Timer ingestionOpenSearchTimes;
    private final Counter failedInOpenSearchCounter;
    private final Counter succeededInOpenSearchCounter;
    private final Timer ingestionPostgresTimes;
    private int batchSize = 0;
    private int nbPendingCycles = 0;

    public MicrometerJobMetricsRecorder(final Configuration configuration) {
        final MeterRegistry registry = BackendRegistries.getDefaultNow();
        if(registry == null) {
            throw new IllegalStateException("micrometer.registries.empty");
        }
        Gauge.builder("ingest.batch.size", () -> batchSize).register(registry);
        Gauge.builder("ingest.pending.cycle.size", () -> nbPendingCycles).register(registry);
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
        ingestCycleWithFailuresCounter = Counter.builder("ingest.cycle.with.failure")
                .description("number of ingest cycles with failures")
                .register(registry);
        succeededMessagesCounter = Counter.builder("ingest.message.succeeded")
                .description("number of succeeded messages")
                .register(registry);
        jobCounter = Counter.builder("ingest.job")
                .description("number of launched jobs")
                .register(registry);
        //----
        final Timer.Builder ingestionCycleTimesBuilder = Timer.builder("ingest.ingestion.cycle.time")
                .description("time by ingestion cycle ");
        if(configuration.slaCycle.isEmpty()) {
            ingestionCycleTimesBuilder
                    .publishPercentileHistogram()
                    .maximumExpectedValue(Duration.ofSeconds(40L));
        } else {
            ingestionCycleTimesBuilder.sla(configuration.slaCycle.toArray(new Duration[0]));
        }
        ingestionCycleTimes = ingestionCycleTimesBuilder.register(registry);
        //----
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
        //----
        final Timer.Builder ingestionOSBuilder = Timer.builder("ingest.ingestion.opensearch.time")
                .description("ingestion time in OpenSearch");
        if(configuration.slaOpensearch.isEmpty()) {
            ingestionOSBuilder
                .publishPercentileHistogram()
                .maximumExpectedValue(Duration.ofMinutes(1L));
        } else {
            ingestionOSBuilder.sla(configuration.slaOpensearch.toArray(new Duration[0]));
        }
        ingestionOpenSearchTimes = ingestionOSBuilder.register(registry);
        //----
        final Timer.Builder ingestionPGBuilder = Timer.builder("ingest.ingestion.postgres.time")
                .description("ingestion time in Postgres");
        if(configuration.slaPostgres.isEmpty()) {
            ingestionPGBuilder
                .publishPercentileHistogram()
                .maximumExpectedValue(Duration.ofMinutes(1L));
        } else {
            ingestionPGBuilder.sla(configuration.slaOpensearch.toArray(new Duration[0]));
        }
        ingestionPostgresTimes = ingestionPGBuilder.register(registry);
        messagesAttemptedTooManyTimesCounter = Counter.builder("ingest.message.too.much.attempts")
                .description("number of messages that were attempted too many times")
                .register(registry);
        failedInOpenSearchCounter = Counter.builder("ingest.message.opensearch.failed")
                .description("number of messages that failed to be ingested in OpenSearch")
                .register(registry);
        succeededInOpenSearchCounter = Counter.builder("ingest.message.opensearch.succeeded")
                .description("number of messages that have successfully been ingested in OpenSearch")
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
        nbPendingCycles--;
    }

    @Override
    public void onIngestCycleResult(IngestJob.IngestJobResult ingestJobResult, MergeMessagesResult mergeResult, final long startTs) {
        if(ingestJobResult.size() > 0) {
            ingestionCycleTimes.record((System.currentTimeMillis() - startTs) / ingestJobResult.size(), TimeUnit.MILLISECONDS);
        }
        final long now = System.currentTimeMillis();
        if(ingestJobResult.getFailed() != null && ingestJobResult.getFailed().isEmpty()) {
            ingestCycleWithFailuresCounter.increment();
        }
        succeededMessagesCounter.increment(ingestJobResult.getSucceed().size());
        for (ExplorerMessageForIngest explorerMessageForIngest : ingestJobResult.getSucceed()) {
            getMessageCreationTime(explorerMessageForIngest).ifPresent(creationTime -> ingestionTimes.record(now - creationTime, TimeUnit.MILLISECONDS));
        }
    }

    private Optional<Long> getMessageCreationTime(final ExplorerMessageForIngest explorerMessageForIngest) {
        final JsonObject message = explorerMessageForIngest.getMessage();
        return Stream.of("createdAt", "deletedAt")
            .filter(message::containsKey)
            .map(message::getLong)
            .findAny();
    }

    @Override
    public void onNewPendingIngestCycle() {
        nbPendingCycles++;
    }

    @Override
    public void onMessagesAttempedTooManyTimes(int nbMessagesAttemptedTooManyTimes) {
        messagesAttemptedTooManyTimesCounter.increment(nbMessagesAttemptedTooManyTimes);
    }

    @Override
    public void onIngestOpenSearchResult(final int nbOk, final int nbKo, final long elapsedTime) {
        failedInOpenSearchCounter.increment(nbKo);
        succeededInOpenSearchCounter.increment(nbOk);
        // On the following line, +1 is to avoid division by zero
        ingestionOpenSearchTimes.record(elapsedTime / (nbOk + nbKo + 1), TimeUnit.MILLISECONDS);
    }

    @Override
    public void onIngestPostgresResult(final long elapsedTime) {
        ingestionPostgresTimes.record(elapsedTime, TimeUnit.MILLISECONDS);
    }

    public static class Configuration {
        private final List<Duration> sla;
        private final List<Duration> slaOpensearch;
        private final List<Duration> slaPostgres;
        private final List<Duration> slaCycle;

        public Configuration(final List<Duration> sla, final List<Duration> slaOpensearch, final List<Duration> slaPostgres,
                             final List<Duration> slaCycle) {
            this.sla = sla;
            this.slaOpensearch = slaOpensearch;
            this.slaPostgres = slaPostgres;
            this.slaCycle = slaCycle;
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
            final List<String> slaNameInConfiguration = new ArrayList<>();
            slaNameInConfiguration.add("sla");
            slaNameInConfiguration.add("slaOpensearch");
            slaNameInConfiguration.add("slaPostgres");
            slaNameInConfiguration.add("slaCycle");
            final List<List<Duration>> slas;
            if(conf == null || !conf.containsKey("metrics")) {
                slas = slaNameInConfiguration.stream().map(e -> new ArrayList<Duration>()).collect(Collectors.toList());
            } else {
                final JsonObject metrics = conf.getJsonObject("metrics");
                slas = slaNameInConfiguration.stream()
                .map(name -> metrics.getJsonArray(name, new JsonArray()).stream()
                    .mapToInt(slaBucket -> (int) slaBucket)
                    .sorted()
                    .mapToObj(Duration::ofMillis)
                    .collect(Collectors.toList()))
                .collect(Collectors.toList());
            }
            return new Configuration(slas.get(0), slas.get(1), slas.get(2), slas.get(3));
        }
    }

    @Override
    public void onBatchSizeUpdate(final int newBatchSize) {
        this.batchSize = newBatchSize;
    }
}
