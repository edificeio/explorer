package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.folders.FolderExplorerCrudSql;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface MessageIngester {

    static MessageIngester elastic(final ElasticClientManager elasticClient) {
        return new MessageIngesterElastic(elasticClient);
    }
    static MessageIngester elasticWithPgBackup(final ElasticClientManager elasticClient, final FolderExplorerCrudSql sql) {
        final MessageIngester ingester = elastic(elasticClient);
        return new MessageIngesterPostgres(sql, ingester);
    }

    Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages);

    Future<JsonObject> getMetrics();

}
