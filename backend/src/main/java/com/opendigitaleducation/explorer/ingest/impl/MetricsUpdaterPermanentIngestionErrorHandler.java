package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.IngestJobMetricsRecorder;
import com.opendigitaleducation.explorer.ingest.PermanentIngestionErrorHandler;
import io.vertx.core.Future;

import java.util.List;

/**
 * Update the metrics count of failed messages.
 */
public class MetricsUpdaterPermanentIngestionErrorHandler implements PermanentIngestionErrorHandler {
  private final IngestJobMetricsRecorder ingestJobMetricsRecorder;

  public MetricsUpdaterPermanentIngestionErrorHandler(final IngestJobMetricsRecorder ingestJobMetricsRecorder) {
    this.ingestJobMetricsRecorder = ingestJobMetricsRecorder;
  }

  @Override
  public Future<Void> handleDeletedMessages(final List<ExplorerMessageForIngest> permanentlyDeletedMessages) {
    if(!permanentlyDeletedMessages.isEmpty()) {
      this.ingestJobMetricsRecorder.onMessagesAttempedTooManyTimes(permanentlyDeletedMessages.size());
    }
    return Future.succeededFuture();
  }
}
