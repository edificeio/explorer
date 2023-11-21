package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.PermanentIngestionErrorHandler;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A chain of error handlers.
 *
 * <b><u>Notes :</u></b>
 * <ul>
 *   <li>The handlers will be called one after the other</li>
 *   <li>If one handler fails it will not prevent the others from processing</li>
 * </ul>
 */
public class ChainPermanentIngestionErrorHandler implements PermanentIngestionErrorHandler {
  public final List<PermanentIngestionErrorHandler> chain;
  public ChainPermanentIngestionErrorHandler(final PermanentIngestionErrorHandler... handlers) {
    chain = new ArrayList<>();
    if(handlers != null) {
      chain.addAll(Arrays.asList(handlers));
    }
  }

  @Override
  public Future<Void> handleDeletedMessages(List<ExplorerMessageForIngest> permanentlyDeletedMessages) {
    return handleChainLink(0, permanentlyDeletedMessages);
  }

  private Future<Void> handleChainLink(int idxChainLink, List<ExplorerMessageForIngest> permanentlyDeletedMessages) {
    if(idxChainLink >= chain.size()) {
      return Future.succeededFuture();
    }
    final Promise<Void> promise = Promise.promise();
    final PermanentIngestionErrorHandler handler = chain.get(idxChainLink);
    handler.handleDeletedMessages(permanentlyDeletedMessages)
    .onComplete(e -> handleChainLink(idxChainLink + 1, permanentlyDeletedMessages)
        .onComplete(onDone -> promise.complete()));
    return promise.future();
  }
}
