package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.postgres.IPostgresClient;

import java.util.List;

public interface MessageIngester {

    static MessageIngester elastic(final ElasticClientManager elasticClient, final IngestJobMetricsRecorder ingestJobMetricsRecorder) {
        return new MessageIngesterElastic(elasticClient, ingestJobMetricsRecorder);
    }
    static MessageIngester elasticWithPgBackup(final ElasticClientManager elasticClient, final IPostgresClient sql, final IngestJobMetricsRecorder ingestJobMetricsRecorder) {
        final MessageIngester ingester = elastic(elasticClient, ingestJobMetricsRecorder);
        return new MessageIngesterPostgres(sql, ingester, ingestJobMetricsRecorder);
    }

    Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages);

}
