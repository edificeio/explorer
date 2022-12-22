package com.opendigitaleducation.explorer.ingest;

/**
 * Recorder of metrics related to the ingestion of messages of the universal explorer.
 */
public interface IngestJobMetricsRecorder {
    /** Register the fact that a job has been started. */
    void onJobStarted();
    /** Register the fact that a job has been stopped. */
    void onJobStopped();
    /** Register the fact that a new cycle of ingestion has been started. */
    void onIngestCycleStarted();
    /** Register the fact that a new cycle of ingestion has succeeded. */
    void onIngestCycleSucceeded();
    /** Register the fact that a new cycle of ingestion has failed. */
    void onIngestCycleFailed();
    /**
     *  Register the fact that a new cycle of ingestion has reached its completion.<br />
     *  <u>NB: </u> This metric can be different from the one produced by onIngestCycleStarted in two cases:
     *  <ul>
     *      <li>A cycle is ongoing, and until the cycle has completed there should be a difference of 1 between
     *      this metric and the one produced by onIngestCycleStarted</li>
     *      <li>An untreated error has occurred and it prevented the job from gracefully ending. Unlike the other case,
     *      the delta between onIngestCycleStarted and onIngestCycleFailed will persist. When such a difference occurs
     *      it usually mean that a bug should be fixed.</li>
     *  </ul>
     * . */
    void onIngestCycleCompleted();

    /**
     * Update metrics based on the result of an ingestion cycle.
     * @param ingestJobResult Succeeded and failed messages during this cycle
     * @param mergeResult The way messages were merged (to know which "true" messages are concerned by the success or
     *                    failure of a treated message)
     */
    void onIngestCycleResult(final IngestJob.IngestJobResult ingestJobResult, MergeMessagesResult mergeResult);

    /**
     * Register the fact that pending executions of ingestion cycles are changing.
     * It lets us know if execution cycles are piling up (because of degraded performance or some process that did not
     * gracefully exit).
     */
    void onPendingIngestCycleExecutionChanged();

    void onMessagesAttempedTooManyTimes(int nbMessagesAttemptedTooManyTimes);

    class NoopIngestJobMetricsRecorder implements IngestJobMetricsRecorder {
        @Override
        public void onJobStarted() {

        }

        @Override
        public void onJobStopped() {

        }

        @Override
        public void onIngestCycleStarted() {

        }

        @Override
        public void onIngestCycleSucceeded() {

        }

        @Override
        public void onIngestCycleFailed() {

        }

        @Override
        public void onIngestCycleCompleted() {

        }

        @Override
        public void onIngestCycleResult(IngestJob.IngestJobResult ingestJobResult, MergeMessagesResult mergeResult) {

        }

        @Override
        public void onPendingIngestCycleExecutionChanged() {

        }

        @Override
        public void onMessagesAttempedTooManyTimes(int nbMessagesAttemptedTooManyTimes) {

        }

    }
}
