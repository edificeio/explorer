package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.Explorer;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.redis.RedisClient;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.vertx.java.busmods.BusModBase;

import java.util.ArrayList;
import java.util.List;

public class IngestJobWorker extends AbstractVerticle {
    static Logger log = LoggerFactory.getLogger(Explorer.class);
    private final IngestJob job;

    public IngestJobWorker(ElasticClientManager elasticClientManager, PostgresClient postgresClient, RedisClient redisClient) {
        //create ingest job
        final JsonObject ingestConfig = config().getJsonObject("ingest");
        final MessageReader reader = MessageReader.redis(redisClient, ingestConfig);
        final MessageIngester ingester = MessageIngester.elasticWithPgBackup(elasticClientManager, postgresClient);
        log.info("Starting ingest job worker... ");
        job = new IngestJob(vertx, reader, ingester, ingestConfig);
    }

    @Override
    public void start(final Promise<Void> startPromise) throws Exception {
        super.start(startPromise);
        final List<Future> futures = new ArrayList<>();
        futures.add(job.start());
        //call start promise
        CompositeFuture.all(futures).onComplete(e->{
            log.info("Ingest job started -> "+e.succeeded());
            startPromise.handle(e.mapEmpty());
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        super.stop(stopPromise);
        final List<Future> futures = new ArrayList<>();
        log.info("Stopping ingest job worker... ");
        futures.add(job.stop());
        //call stop promise
        CompositeFuture.all(futures).onComplete(e->{
            log.info("Ingest job stopped -> "+e.succeeded());
            stopPromise.handle(e.mapEmpty());
        });
    }
}
