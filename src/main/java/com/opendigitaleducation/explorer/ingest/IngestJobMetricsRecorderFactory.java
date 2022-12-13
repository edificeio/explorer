package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ingest.impl.MicrometerJobMetricsRecorder;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;

/**
 * Creates the singleton that will record metrics of the ingestion job plugin.
 * So far, it only handles MicroMeter implementation. If metricsOptions are not
 * configured then it creates a dummy recorder that records nothing.
 */
public class IngestJobMetricsRecorderFactory {
    private static MetricsOptions metricsOptions;
    private static IngestJobMetricsRecorder ingestJobMetricsRecorder;
    public static void init(final Vertx vertx, final JsonObject config){
        if(config.getJsonObject("metricsOptions") == null) {
            final String metricsOptions = (String) vertx.sharedData().getLocalMap("server").get("metricsOptions");
            if(metricsOptions == null){
                IngestJobMetricsRecorderFactory.metricsOptions = new MetricsOptions().setEnabled(false);
            }else{
                IngestJobMetricsRecorderFactory.metricsOptions = new MetricsOptions(new JsonObject(metricsOptions));
            }
        } else {
            metricsOptions = config.getJsonObject("metricsOptions").mapTo(MetricsOptions.class);
        }
    }

    /**
     * @return The backend to record metrics. If metricsOptions is defined in the configuration then the backend used
     * is MicroMeter. Otherwise a dummy registrar is returned and it collects nothing.
     */
    public static IngestJobMetricsRecorder getIngestJobMetricsRecorder() {
        if(ingestJobMetricsRecorder == null) {
            if(metricsOptions == null) {
                throw new IllegalStateException("ingest.job.metricsrecorder.factory.not.set");
            }
            if(metricsOptions.isEnabled()) {
                ingestJobMetricsRecorder = new MicrometerJobMetricsRecorder();
            } else {
                ingestJobMetricsRecorder = new IngestJobMetricsRecorder.NoopIngestJobMetricsRecorder();
            }
        }
        return ingestJobMetricsRecorder;
    }
}