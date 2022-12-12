package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import org.entcore.common.elasticsearch.ElasticClient;

public interface ExplorerElasticOperation {
    ExplorerMessageForIngest getMessage();

    default String getId() {
        return getMessage().getPredictibleId().orElse(getMessage().getId());
    }

    default String getIndex() {
        return ExplorerConfig.getInstance().getIndex(getMessage().getApplication(), getMessage().getResourceType());
    }


    default ElasticClient.ElasticOptions getOptions() {
        final ExplorerMessageForIngest message = getMessage();
        final String routing = ResourceServiceElastic.getRoutingKey(message.getApplication());
        return new ElasticClient.ElasticOptions()
                .withRouting(routing)
                .withWaitFor(true);
    }
}
