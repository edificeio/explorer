package com.opendigitaleducation.explorer.share;

import io.vertx.core.Future;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ShareTableManager {
    Future<Optional<String>> getOrCreateNewShare(Set<String> userIds, Set<String> groupIds) throws Exception;

    Future<List<String>> findHashes(UserInfos user);
}
