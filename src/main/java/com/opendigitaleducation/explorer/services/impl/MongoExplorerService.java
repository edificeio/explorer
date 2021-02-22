package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.services.ExplorerService;
import io.vertx.core.Future;

import java.util.List;

public class MongoExplorerService implements ExplorerService {
    @Override
    public Future<Void> push(ExplorerMessageBuilder message) {
        //TODO
        return null;
    }

    @Override
    public Future<Void> push(List<ExplorerMessageBuilder> messages) {
        //TODO
        return null;
    }
}
