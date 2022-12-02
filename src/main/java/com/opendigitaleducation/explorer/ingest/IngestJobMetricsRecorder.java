package com.opendigitaleducation.explorer.ingest;

public interface IngestJobMetricsRecorder {
    void onIngestCycleStarted();
    void onIngestCycleSucceeded();
    void onIngestCycleFailed();
    void onIngestCycleCompleted();
    void onIngestCycleResult(final IngestJob.IngestJobResult ingestJobResult, MergeMessagesResult mergeResult);

    void onPendingIngestCycleExecutionChanged(int size);

    public static class NoopIngestJobMetricsRecorder implements IngestJobMetricsRecorder {
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
        public void onPendingIngestCycleExecutionChanged(int size) {

        }
    }
}
