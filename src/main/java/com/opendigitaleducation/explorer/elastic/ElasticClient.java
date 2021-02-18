package com.opendigitaleducation.explorer.elastic;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class ElasticClient {
    private final HttpClient httpClient;
    private Handler<Throwable> onError = (e) -> {
    };

    public ElasticClient(final HttpClient aClient) {
        this.httpClient = aClient;
    }

    public void setOnError(final Handler<Throwable> onError) {
        this.onError = onError;
    }

    public Future<Void> createMapping(final String index, final Buffer payload) {
        final Future<Void> future = Future.future();
        httpClient.put("/" + index).handler(res -> {
            if (res.statusCode() == 200 || res.statusCode() == 201) {
                future.complete();
            } else {
                future.fail(res.statusCode() + ":" + res.statusMessage());
            }
        }).putHeader("content-type", "application/json")
                .exceptionHandler(onError).end(payload);
        return future;
    }

    public Future<String> createDocument(final String index, final JsonObject payload, final ElasticOptions options) {
        final Future<String> future = Future.future();
        final String queryParams = options.getQueryParams();
        httpClient.post("/" + index + "/_doc"+queryParams).handler(res -> {
            if (res.statusCode() == 200 || res.statusCode() == 201) {
                res.bodyHandler(resBody -> {
                    final JsonObject body = new JsonObject(resBody.toString());
                    future.complete(body.getString("_id"));
                });
            } else {
                future.fail(res.statusCode() + ":" + res.statusMessage());
            }
        }).putHeader("content-type", "application/json").exceptionHandler(onError).end(payload.toString());
        return future;
    }

    public Future<JsonArray> search(final String index, final JsonObject payload, final ElasticOptions options) {
        final Future<JsonArray> future = Future.future();
        final String queryParams = options.getQueryParams();
        httpClient.post("/" + index + "/_search"+queryParams).handler(res -> {
            if (res.statusCode() == 200) {
                res.bodyHandler(resBody -> {
                    final JsonObject body = new JsonObject(resBody.toString());
                    final JsonArray hits = body.getJsonObject("hits").getJsonArray("hits");
                    final JsonArray mapped = new JsonArray(hits.stream().map(o->{
                        final JsonObject json = (JsonObject)o;
                        return json.getJsonObject("_source").put("_id", json.getString("_id"));
                    }).collect(Collectors.toList()));
                    future.complete(mapped);
                });
            } else {
                future.fail(res.statusCode() + ":" + res.statusMessage());
            }
        }).putHeader("content-type", "application/json").exceptionHandler(onError).end(payload.toString());
        return future;
    }

    public ElasticBulkRequest bulk(final String index, final ElasticOptions options) {
        final Future<Buffer> future = Future.future();
        final String queryParams = options.getQueryParams();
        final HttpClientRequest req = httpClient.post("/" + index + "/_bulk"+queryParams).handler(res -> {
            if (res.statusCode() == 200 || res.statusCode() == 201) {
                res.bodyHandler(resBody -> {
                    future.complete(resBody);
                });
            } else {
                future.fail(res.statusCode() + ":" + res.statusMessage());
            }
        }).putHeader("Content-Type", "application/x-ndjson")
                .putHeader("Accept", "application/json; charset=UTF-8")
                .setChunked(true).exceptionHandler(onError);
        return new ElasticBulkRequest(req, future);
    }

    public static class ElasticOptions{
        private boolean waitFor = false;
        private List<String> routing = new ArrayList<>();

        public ElasticOptions withWaitFor(boolean waitFor) {
            this.waitFor = waitFor;
            return this;
        }

        public ElasticOptions withRouting(String routing) {
            this.routing.add(routing);
            return this;
        }

        public String getQueryParams(){
           final List<String> queryParams = new ArrayList<>();
            if(waitFor){
                queryParams.add("refresh=wait_for");
            }
            if(!routing.isEmpty()){
                queryParams.add("routing="+String.join(",",routing));
            }
            if(queryParams.isEmpty()){
                return "";
            }else{
                return "?"+String.join("&", queryParams);
            }
        }
    }

}
