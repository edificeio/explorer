package com.opendigitaleducation.explorer.controllers;

import com.opendigitaleducation.explorer.Explorer;

import fr.wseduc.security.ActionType;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import fr.wseduc.rs.*;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.utils.StringUtils;

import java.util.UUID;

public class ExplorerController extends BaseController {
	private final EventHelper eventHelper;
	private static final Logger log = LoggerFactory.getLogger(ExplorerController.class);

	public ExplorerController(){
		final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Explorer.class.getSimpleName());
		this.eventHelper = new EventHelper(eventStore);
	}

	@Get("")
	@SecuredAction("explorer.view")
	public void view(HttpServerRequest request) {
		renderView(request);
	}
}
