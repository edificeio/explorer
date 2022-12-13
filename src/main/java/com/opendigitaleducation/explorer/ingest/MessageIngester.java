package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.Future;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.postgres.IPostgresClient;

import java.util.List;

public interface MessageIngester {

    static MessageIngester elastic(final ElasticClientManager elasticClient) {
        return new MessageIngesterElastic(elasticClient);
    }
    static MessageIngester elasticWithPgBackup(final ElasticClientManager elasticClient, final IPostgresClient sql) {
        final MessageIngester ingester = elastic(elasticClient);
        return new MessageIngesterPostgres(sql, ingester);
    }

    Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages);

}
