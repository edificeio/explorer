package com.opendigitaleducation.explorer.jobs;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface ResourceLoader {
    void start();

    void stop();

    Future<Void> execute();

    void setOnEnd(final Handler<AsyncResult<ResourceLoaderResult>> handler);

    class ResourceLoaderResult {
        final List<JsonObject> succeed;
        final List<JsonObject> failed;

        public ResourceLoaderResult(List<JsonObject> succeed, List<JsonObject> failed) {
            this.succeed = succeed;
            this.failed = failed;
        }

        public List<JsonObject> getSucceed() {
            return succeed;
        }

        public List<JsonObject> getFailed() {
            return failed;
        }
    }
}
