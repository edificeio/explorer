package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.Explorer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.postgres.IPostgresClient;

import java.util.ArrayList;
import java.util.List;

public class IngestJobWorker extends AbstractVerticle {
    static Logger log = LoggerFactory.getLogger(Explorer.class);
    private IngestJob job;

    @Override
    public void start(final Promise<Void> startPromise) throws Exception {
        IngestJobMetricsRecorderFactory.init(vertx, config());
        final ElasticClientManager elasticClientManager = ElasticClientManager.create(vertx, config());
        final boolean runjobInWroker = config().getBoolean("worker-job", true);
        final IPostgresClient postgresClient = IPostgresClient.create(vertx, config(), runjobInWroker, false);
        //create ingest job
        final JsonObject ingestConfig = config().getJsonObject("ingest");
        final MessageReader reader = MessageReader.create(vertx, config(), ingestConfig);
        final IngestJobMetricsRecorder metricsRecorder = IngestJobMetricsRecorderFactory.getIngestJobMetricsRecorder();
        final MessageIngester ingester = MessageIngester.elasticWithPgBackup(elasticClientManager, postgresClient, metricsRecorder);
        log.info("Starting ingest job worker... ");
        job = new IngestJob(vertx, reader, ingester, metricsRecorder, ingestConfig);
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
