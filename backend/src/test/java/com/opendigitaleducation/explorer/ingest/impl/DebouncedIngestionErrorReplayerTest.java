package com.opendigitaleducation.explorer.ingest.impl;


import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.explorer.ExplorerMessage;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

@RunWith(VertxUnitRunner.class)
public class DebouncedIngestionErrorReplayerTest {

  /**
   *  <h1>GOAL</h1>
   *  <p>
   *    Ensure that messages are correctly sent after the debounce delay period when no failed messages are reported.
   *  </p>
   * @param context Context
   */
  @Test
  public void testDebounceDelayIsRespectedWhenNoMessageInserted(final TestContext context) {
    final Async async = context.async();
    final Vertx vertx = Vertx.vertx();
    final DebouncedIngestionErrorReplayer replayer = new DebouncedIngestionErrorReplayer(vertx, 2000L, -1);
    replayer.handleDeletedMessages(singletonList(
        new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue", "myId", message("app", "type"))
    ));
    context.assertEquals(1L, replayer.getSize(), "There should be one pending resource to send");
    vertx.setTimer(1000L, e -> context.assertEquals(1L, replayer.getSize(), "There should still be only one pending resource to send"));
    vertx.setTimer(2100L, e -> {
      context.assertEquals(0L, replayer.getSize(), "There should be no pending resources to send");
      async.complete();
    });
  }

  /**
   *  <h1>GOAL</h1>
   *  <p>
   *    Ensure that messages are correctly sent after the debounce delay period when no failed messages are reported.
   *  </p>
   * @param context Context
   */
  @Test
  public void testDebounceDelayIsNotRespectedWhenTooMuchMessagesArePending(final TestContext context) {
    final Vertx vertx = Vertx.vertx();
    final DebouncedIngestionErrorReplayer replayer = new DebouncedIngestionErrorReplayer(vertx, 20000L, 3);
    replayer.handleDeletedMessages(singletonList(
        new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue", "myId", message("app", "type"))
    ));
    context.assertEquals(1L, replayer.getSize(), "There should be one pending resource to send");
    replayer.handleDeletedMessages(singletonList(
        new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue2", "myId2", message("app", "type"))
    ));
    context.assertEquals(2L, replayer.getSize(), "There should be 2 pending resources to send because we haven't reached max length");
    replayer.handleDeletedMessages(singletonList(
        new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue2", "myId3", message("app", "type"))
    ));
    context.assertEquals(0L, replayer.getSize(), "There should be no pending resources to send because we have reached max length");
  }
  /**
   *  <h1>GOAL</h1>
   *  <p>
   *    Ensure that messages of all apps are correctly sent.
   *  </p>
   *  <ol>
   *    <li>Create 2 messages for app A</li>
   *    <li>Create 1 message for app B with the id of one of the message of app A</li>
   *    <li>Check that 3 messages are to be sent</li>
   *  </ol>
   * @param context Context
   */
  @Test
  public void testSplitResourcesByAppAndType(final TestContext context) {
    final Vertx vertx = Vertx.vertx();
    final DebouncedIngestionErrorReplayer replayer = new DebouncedIngestionErrorReplayer(vertx, 100L, -1);
    final List<ExplorerMessageForIngest> messages = new ArrayList<>();
    messages.add(new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue", "myId", message("app", "type")));
    messages.add(new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue", "myId2", message("app", "type")));
    messages.add(new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue", "myId", message("app2", "type")));
    replayer.handleDeletedMessages(messages);
    context.assertEquals(3L, replayer.getSize(), "Should have all three items and not only 2 because the two that share the same id have different apps");
  }

  /**
   *  <h1>GOAL</h1>
   *  <p>
   *    Ensure that messages are correctly sent after the debounce delay period when it has been reset after
   *    a new failed message has been reported.
   *  </p>
   * @param context Context
   */
  @Test
  public void testDebounceDelayIsRespectedWhenMessageInserted(final TestContext context) {
    final Async async = context.async();
    final Vertx vertx = Vertx.vertx();
    final DebouncedIngestionErrorReplayer replayer = new DebouncedIngestionErrorReplayer(vertx, 2000L, -1);
    replayer.handleDeletedMessages(singletonList(
        new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue", "myId", message("app", "type"))
    ));
    context.assertEquals(1L, replayer.getSize(), "There should be one pending resource to send");
    vertx.setTimer(1000L, e -> replayer.handleDeletedMessages(singletonList(
        new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue2", "myId2", message("app", "type"))
    )));
    vertx.setTimer(2100L, e -> {
      context.assertEquals(2L, replayer.getSize(), "Both messages should still be in the queue. It means that the debounce delay was not reset");
    });
    vertx.setTimer(3100L, e -> {
      context.assertEquals(0L, replayer.getSize(), "The queue should be empty after the second debounce delay.");
      async.complete();
    });
  }


  /**
   *  <h1>GOAL</h1>
   *  <p>
   *    Ensure that if multiple failing messages for the same resource won't trigger a reset of the sending delay
   *  </p>
   *  <h1>Steps</h1>
   *  <ol>
   *    <li>add a message for a resource at t0</li>
   *    <li>wait for a short amount of time dt</li>
   *    <li>add a new message</li>
   *    <li>ensure that the length of the queue hasn't changed</li>
   *    <li>check at t0 + debounceDelay that all messages have been sent</li>
   *  </ol>
   * @param context Context
   */
  @Test
  public void testDoNotResetDelayWhenAMessageConcerningTheSameResourceIsAdded(final TestContext context) {
    final Async async = context.async();
    final Vertx vertx = Vertx.vertx();
    final DebouncedIngestionErrorReplayer replayer = new DebouncedIngestionErrorReplayer(vertx, 1000L, -1);
    replayer.handleDeletedMessages(singletonList(
        new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue", "myId", message("app", "type"))
    ));
    context.assertEquals(1L, replayer.getSize(), "There should be one pending resource to send");
    vertx.setTimer(500L, e -> {
      replayer.handleDeletedMessages(singletonList(
          new ExplorerMessageForIngest(ExplorerMessage.ExplorerAction.Audience.name(), "idQueue2", "myId", message("app", "type"))
      ));
      context.assertEquals(1L, replayer.getSize(), "There should still be only one pending resource to send because the added message reports to the same id");
    });
    vertx.setTimer(1100L, e -> {
      context.assertEquals(0L, replayer.getSize(), "There should be no pending resources to send");
      async.complete();
    });
  }

  public static JsonObject message(final String app, final String type) {
    return new JsonObject()
        .put("application", app)
        .put("resourceType", type);
  }
}
