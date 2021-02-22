package com.opendigitaleducation.explorer.jobs;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class MongoResourceLoader implements ResourceLoader {
    @Override
    public Future<Void> start() {
        //TODO listen
        return null;
    }

    @Override
    public boolean isStarted() {
        //TODO
        return false;
    }

    @Override
    public Future<Void> stop() {
        //TODO stop listen
        return null;
    }

    @Override
    public Future<Void> execute(boolean force) {
        //TODO
        return null;
    }

    @Override
    public void setOnEnd(Handler<AsyncResult<ResourceLoaderResult>> handler) {
        //TODO
    }
}
