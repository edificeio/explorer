package com.opendigitaleducation.explorer.plugin;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.function.Function;

public interface ExplorerPluginCommunication {

    Future<Void> pushMessage(final ExplorerMessage message);

    Future<Void> pushMessage(final List<ExplorerMessage> messages);

    Function<Void, Void> listen(final String id, final Handler<Message<JsonObject>> onMessage);
}
