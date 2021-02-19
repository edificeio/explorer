package com.opendigitaleducation.explorer.jobs;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class RedisResourceLoader  implements ResourceLoader{
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
    public void stop() {
        //TODO stop listen
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
