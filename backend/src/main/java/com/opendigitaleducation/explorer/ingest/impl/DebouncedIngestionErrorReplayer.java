package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.PermanentIngestionErrorHandler;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.to.ExplorerReindexResourcesRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Accumulate error messages for a configurable maximum period of time and then call the client app to reindex the
 * resources that generated the failures.
 */
public class DebouncedIngestionErrorReplayer implements PermanentIngestionErrorHandler {

  public static final Logger log = LoggerFactory.getLogger(DebouncedIngestionErrorReplayer.class);
  private final Map<MessageToSendKey, Set<String>> messagesToSend = new HashMap<>();
  private final Vertx vertx;
  /** The time (in milliseconds) that this service will wait before sending pending messages if no new messages have arrived.*/
  private final long debounceDelay;
  /** Maximum number of messages that can be held waiting. As soon as this limit is reached, the pending messages will be immediately sent.*/
  private final int maxLength;
  private Long taskId = null;

  /**
   *
   * @param vertx Vertx instance
   * @param debounceDelay The delay of inactivity before sending the waiting messages
   * @param maxLength Maximum number of messages to stack before sending them
   */
  public DebouncedIngestionErrorReplayer(final Vertx vertx,
                                         final long debounceDelay,
                                         final int maxLength) {
    this.vertx = vertx;
    this.debounceDelay = debounceDelay;
    this.maxLength = maxLength;
  }

  @Override
  public Future<Void> handleDeletedMessages(final List<ExplorerMessageForIngest> permanentlyDeletedMessages) {
    if(!permanentlyDeletedMessages.isEmpty()) {
      final boolean hasChanged = addNewMessages(permanentlyDeletedMessages);
      if(hasChanged) {
        if (taskId != null) {
          vertx.cancelTimer(taskId);
        }
        if(debounceDelay <= 0 || (maxLength > 0 && getSize() >= maxLength)) {
          sendMessages();
        } else {
          taskId = vertx.setTimer(debounceDelay, l -> sendMessages());
        }
      }
    }
    return Future.succeededFuture();
  }

  private boolean addNewMessages(final List<ExplorerMessageForIngest> permanentlyDeletedMessages) {
    boolean modified = false;
    for (ExplorerMessageForIngest deletedMessage : permanentlyDeletedMessages) {
      final String application = deletedMessage.getApplication();
      final String type = deletedMessage.getResourceType();
      final MessageToSendKey key = new MessageToSendKey(application, type);
      final Set<String> messages = messagesToSend.computeIfAbsent(key, k -> new HashSet<>());
      final String parentId = deletedMessage.getId();
      if(!messages.contains(parentId)) {
        messages.add(parentId);
        modified = true;
      }
    }
    return modified;
  }

  /**
   * Immediately send pending messages.
   * @return A Future that completes when the cache of messages to send have been cleared.
   */
  public Future<Void> sendMessages() {
    final Future<Void> completion;
    if(messagesToSend.isEmpty()) {
      log.debug("[DebouncedIngestionErrorReplayer@sendMessages] Nothing to send");
      completion = Future.succeededFuture();
    } else {
      log.info("[DebouncedIngestionErrorReplayer@sendMessages] Sending messages");
      final List<Future<Void>> futures = messagesToSend.entrySet().stream().map(entry -> {
        final MessageToSendKey key = entry.getKey();
        final Set<String> value = entry.getValue();
        final Promise<Void> promise = Promise.promise();
        final IExplorerPluginClient client = IExplorerPluginClient.withBus(vertx, key.app, key.type);
        client.reindex(new ExplorerReindexResourcesRequest(value))
            .onComplete(e -> {
              log.info("[DebouncedIngestionErrorReplayer@sendMessages] Done reindexing " + key);
              promise.complete();
            });
        return promise.future();
      }).collect(Collectors.toList());
      messagesToSend.clear();
      completion = CompositeFuture.join((List)futures).mapEmpty();
    }
    taskId = null;
    return completion;
  }

  /**
   * @return The number of messages to be send
   */
  public long getSize() {
    return messagesToSend.values().stream()
        .mapToLong(Set::size)
        .sum();
  }

  private static class MessageToSendKey {
    private final String app;
    private final String type;

    private MessageToSendKey(String app, String type) {
      this.app = app;
      this.type = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MessageToSendKey that = (MessageToSendKey) o;
      return Objects.equals(app, that.app) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(app, type);
    }

    @Override
    public String toString() {
      return "MessageToSendKey{" +
          "app='" + app + '\'' +
          ", type='" + type + '\'' +
          '}';
    }
  }
}
