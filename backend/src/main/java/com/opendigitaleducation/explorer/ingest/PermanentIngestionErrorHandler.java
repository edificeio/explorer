package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;

import java.util.List;

/**
 * Service that handles downstream treatments on failed messages.
 */
public interface PermanentIngestionErrorHandler {
  /**
   * Process the messages and complete the {@code Future} as soon as the treatment is done.
   * @param permanentlyDeletedMessages Messages that have been played too many times and that will be evicted from the
   *                                   ingestion job
   * @return A completion signal
   */
  Future<Void> handleDeletedMessages(final List<ExplorerMessageForIngest> permanentlyDeletedMessages);
}
