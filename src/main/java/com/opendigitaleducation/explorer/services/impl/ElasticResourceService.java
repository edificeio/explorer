package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.elastic.ElasticBulkRequest;
import com.opendigitaleducation.explorer.elastic.ElasticClient;
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.services.ResourceService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ElasticResourceService implements ResourceService {
    final ElasticClientManager manager;
    final String index;
    final boolean waitFor = true;

    public ElasticResourceService(final ElasticClientManager aManager) {
        this(aManager, DEFAULT_RESOURCE_INDEX);
    }

    public ElasticResourceService(final ElasticClientManager aManager, final String index) {
        this.manager = aManager;
        this.index = index;
    }

    @Override
    public Future<List<JsonObject>> bulkOperations(List<ResourceBulkOperation> operations) {
        if(operations.isEmpty()){
            return Future.succeededFuture(new ArrayList<>());
        }
        final ElasticBulkRequest bulk = manager.getClient().bulk(index, new ElasticClient.ElasticOptions().withWaitFor(waitFor));
        for (final ResourceBulkOperation op : operations) {
            final String routing = getRoutingKey(op.getResource());
            final String id = op.getResource().getString("_id");
            switch (op.getType()) {
                case Create:
                    bulk.create(op.getResource(), Optional.ofNullable(id), Optional.empty(), Optional.of(routing));
                    break;
                case Delete:
                    bulk.delete(id, Optional.empty(), Optional.of(routing));
                    break;
                case Update:
                    bulk.update(op.getResource(), id, Optional.empty(), Optional.of(routing));
                    break;
            }
        }
        return bulk.end().compose(results -> {
            final List<JsonObject> resources = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                final ElasticBulkRequest.ElasticBulkRequestResult res = results.get(i);
                final JsonObject resource = operations.get(i).getResource();
                if (res.isOk()) {
                    resource.put(SUCCESS_FIELD, true);
                } else {
                    resource.put(ERROR_FIELD, res.getMessage());
                    resource.put(SUCCESS_FIELD, false);
                }
                resources.add(resource);
            }
            return Future.succeededFuture(resources);
        });
    }

    protected String getRoutingKey(final JsonObject resource) {
        return getRoutingKey(resource.getString("application"), resource.getString("resourceType"));
    }

    protected String getRoutingKey(final String application, final String resourceType) {
        return application + ":" + resourceType;
    }
}
