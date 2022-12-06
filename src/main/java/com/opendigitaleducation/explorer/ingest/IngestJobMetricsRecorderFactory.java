package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ingest.impl.MicrometerJobMetricsRecorder;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;

/**
 * Creates the singleton that will record metrics of the ingestion job plugin.
 * So far, it only handles MicroMeter implementation. If Micrometer is not
 * configured then it creates a dummy recorder that records nothing.
 */
public class IngestJobMetricsRecorderFactory {
    private static JsonObject globalConfig;
    private static IngestJobMetricsRecorder ingestJobMetricsRecorder;
    public static void init(final JsonObject config){
        globalConfig = config;
    }

    public static IngestJobMetricsRecorder getIngestJobMetricsRecorder() {
        if(ingestJobMetricsRecorder == null) {
            if(globalConfig == null) {
                throw new IllegalStateException("ingest.job.metricsrecorder.factory.not.set");
            }
            final JsonObject metricsOptionsRaw = globalConfig.getJsonObject("metricsOptions");
            if(metricsOptionsRaw == null) {
                ingestJobMetricsRecorder = new IngestJobMetricsRecorder.NoopIngestJobMetricsRecorder();
            } else {
                final MetricsOptions metricsOptions = metricsOptionsRaw.mapTo(MetricsOptions.class);
                if(metricsOptions.isEnabled() && metricsOptions instanceof MicrometerMetricsOptions) {
                    ingestJobMetricsRecorder = new MicrometerJobMetricsRecorder();
                } else {
                    ingestJobMetricsRecorder = new IngestJobMetricsRecorder.NoopIngestJobMetricsRecorder();
                }
            }
        }
        return ingestJobMetricsRecorder;
    }
}
