package com.opendigitaleducation.explorer.elastic;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticSearch;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//TODO merge with entcore (onerror circuit breaker...)
public class ElasticClientManager {
    static final Logger log = LoggerFactory.getLogger(ElasticClientManager.class);
    private final Random rnd = new Random();
    private List<ElasticClient> clients = new ArrayList<>();
    public ElasticClientManager(final Vertx vertx, final URI[] uris) {
     this(vertx, uris, new JsonObject());
    }
    public ElasticClientManager(final Vertx vertx, final URI[] uris, final JsonObject config){
        try {
            final int poolSize = config.getInteger("poolSize", 16);
            final boolean keepAlive = config.getBoolean("keepAlive", true);
            for (final URI uri : uris) {
                HttpClientOptions httpClientOptions = new HttpClientOptions()
                        .setKeepAlive(keepAlive)
                        .setMaxPoolSize(poolSize)
                        .setDefaultHost(uri.getHost())
                        .setDefaultPort(uri.getPort())
                        .setConnectTimeout(20000);
                clients.add(new ElasticClient(vertx.createHttpClient(httpClientOptions)));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public ElasticClient getClient() {
        return clients.get(rnd.nextInt(clients.size()));
    }
}
