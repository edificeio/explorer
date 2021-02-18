package com.opendigitaleducation.explorer.controllers;

import com.opendigitaleducation.explorer.Explorer;
import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;

public class ExplorerController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(ExplorerController.class);
    private final EventHelper eventHelper;

    public ExplorerController() {
        final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Explorer.class.getSimpleName());
        this.eventHelper = new EventHelper(eventStore);
    }

    @Get("")
    @SecuredAction("explorer.view")
    public void view(HttpServerRequest request) {
        renderView(request);
    }
}
