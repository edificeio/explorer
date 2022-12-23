package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import org.entcore.common.explorer.to.MuteRequest;
import org.entcore.common.user.UserInfos;

import java.util.Set;

public interface MuteService {

    /**
     * Mutes the resources specified in the request
     * @param userInfos Information of the caller
     * @param muteRequest Information concerning the resources to mute
     * @return A future that completes if everything went fine and fails otherwose
     */
    Future<Void> mute(UserInfos userInfos, MuteRequest muteRequest);

    /**
     * Get the ids of the users who have muted the resource.
     *
     * @param id        Id of the resource
     * @param userInfos Information concerning the resources to mute
     * @return
     */
    Future<Set<String>> getMutedBy(final String id, final UserInfos userInfos);
}
