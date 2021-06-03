package com.opendigitaleducation.explorer.plugin;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public abstract class ExplorerPluginClient {

    public static ExplorerPluginClient withBus(final Vertx vertx, final String application, final String type){
        return new ExplorerPluginClientDefault(vertx, application, type);
    }

    public Future<IndexResponse> getForIndexation(final UserInfos user, final Optional<Date> from, final Optional<Date> to){
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("action", ExplorerPlugin.ExplorerRemoteAction.QueryReindex.name());
        headers.add("userId", user.getUserId());
        headers.add("userName", user.getUsername());
        final JsonObject payload = new JsonObject();
        if(from.isPresent()){
            payload.put("from", from.get().getTime());
        }
        if(to.isPresent()){
            payload.put("to", to.get().getTime());
        }
        //nb_message,nb_batch
        final Future<JsonObject> future = send(headers, payload, Duration.ofDays(100));
        return future.map(res->{
            final int nb_message = res.getInteger("nb_message");
            final int nb_batch = res.getInteger("nb_batch");
            return new IndexResponse(nb_batch, nb_message);
        });
    }

    public Future<List<String>> createAll(final UserInfos user, final List<JsonObject> json, final boolean isCopy){
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("action", ExplorerPlugin.ExplorerRemoteAction.QueryCreate.name());
        headers.add("userId", user.getUserId());
        headers.add("userName", user.getUsername());
        final JsonObject payload = new JsonObject();
        payload.put("resources", json);
        payload.put("copy", isCopy);
        final Future<JsonArray> future = send(headers, payload, Duration.ofMinutes(10));
        return future.map(jsonarray->{
            return jsonarray.stream().map(id-> (String)id).collect(Collectors.toList());
        });
    }

    public Future<DeleteResponse> deleteById(final UserInfos user, final Set<String> ids){
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("action", ExplorerPlugin.ExplorerRemoteAction.QueryDelete.name());
        headers.add("userId", user.getUserId());
        headers.add("userName", user.getUsername());
        final JsonObject payload = new JsonObject();
        payload.put("resources", new JsonArray(new ArrayList(ids)));
        final Future<JsonObject> future = send(headers, payload, Duration.ofMinutes(5));
        //deleted, failed
        return future.map(res->{
            final List<String> deleted = res.getJsonArray("deleted").stream().map(id-> (String)id).collect(Collectors.toList());
            final List<String> failed = res.getJsonArray("failed").stream().map(id-> (String)id).collect(Collectors.toList());
            final DeleteResponse delRes = new DeleteResponse();
            delRes.deleted.addAll(deleted);
            delRes.notDeleted.addAll(failed);
            return delRes;
        });
    }

    public Future<JsonObject> getMetrics(final UserInfos user){
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("action", ExplorerPlugin.ExplorerRemoteAction.QueryMetrics.name());
        headers.add("userId", user.getUserId());
        headers.add("userName", user.getUsername());
        final Future<JsonObject> future = send(headers, new JsonObject(), Duration.ofMinutes(10));
        return future;
    }

    abstract protected <T> Future<T> send(final MultiMap headers, final JsonObject payload, final Duration timeout);


    public static class DeleteResponse{
        public final List<String> deleted = new ArrayList<>();
        public final List<String> notDeleted = new ArrayList<>();
    }
    public static class IndexResponse{
        public final int nbBatch;
        public final int nbMessage;

        public IndexResponse(int nbBatch, int nbMessage) {
            this.nbBatch = nbBatch;
            this.nbMessage = nbMessage;
        }

        public JsonObject toJson(){
            return new JsonObject().put("nbBatch", nbBatch).put("nbMessage", nbMessage);
        }

    }
}
