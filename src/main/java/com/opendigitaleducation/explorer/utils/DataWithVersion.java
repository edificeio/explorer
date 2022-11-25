package com.opendigitaleducation.explorer.utils;

import io.netty.util.internal.UnstableApi;
import io.vertx.core.json.JsonObject;

/**
 * Holds an entity along with its version.
 */
@UnstableApi
public class DataWithVersion {
    private final JsonObject data;
    private final long version;

    public DataWithVersion(JsonObject data, long version) {
        this.data = data;
        this.version = version;
    }

    public JsonObject getData() {
        return data;
    }

    public long getVersion() {
        return version;
    }
}
