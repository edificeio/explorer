package com.opendigitaleducation.explorer.controllers;


import com.opendigitaleducation.explorer.services.MuteService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.to.MuteRequest;

import static io.vertx.core.CompositeFuture.all;
import static org.entcore.common.user.UserUtils.getAuthenticatedUserInfos;

/**
 * Entrypoint for all actions related to muting resources and folders.
 * Muting resources requests generally come from users and checks can come
 * from both users and timeline.
 */
public class MuteController extends BaseController {

    private final MuteService muteService;

    public MuteController(final MuteService muteService) {
        this.muteService = muteService;
    }

    /**
     * Mute resources.
     * @param request
     */
    @Put("mute")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void mute(final HttpServerRequest request) {
        all(
            getAuthenticatedUserInfos(eb, request),
            RequestUtils.bodyToClass(request, MuteRequest.class)
        )
        .compose(userInfos -> muteService.mute(userInfos.resultAt(0), userInfos.resultAt(1)))
        .onSuccess(e -> renderJson(request, new JsonObject()))
        .onFailure(th -> renderError(request));
    }

}
