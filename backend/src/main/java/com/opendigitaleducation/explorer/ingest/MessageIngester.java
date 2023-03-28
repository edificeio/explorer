package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.postgres.IPostgresClient;

import java.util.List;

public interface MessageIngester {

    static MessageIngester elastic(final ElasticClientManager elasticClient,
                                   final IngestJobMetricsRecorder ingestJobMetricsRecorder,
                                   final JsonObject config) {
        return new MessageIngesterElastic(elasticClient, ingestJobMetricsRecorder, config);
    }
    static MessageIngester elasticWithPgBackup(final ElasticClientManager elasticClient,
                                               final IPostgresClient sql,
                                               final IngestJobMetricsRecorder ingestJobMetricsRecorder,
                                               final JsonObject config) {
        final MessageIngester ingester = elastic(elasticClient, ingestJobMetricsRecorder, config);
        return new MessageIngesterPostgres(sql, ingester, ingestJobMetricsRecorder);
    }

    Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages);

}
