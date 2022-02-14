package com.opendigitaleducation.explorer.share;

import com.opendigitaleducation.explorer.ExplorerConfig;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.postgres.PostgresClientPool;
import fr.wseduc.webutils.security.Md5;
import io.reactiverse.pgclient.Row;
import io.reactiverse.pgclient.Tuple;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class DefaultShareTableManager implements ShareTableManager {

    protected String text(final List<String> userIds, final List<String> groupIds) {
        userIds.sort(Comparator.comparing(String::toString));
        groupIds.sort(Comparator.comparing(String::toString));
        final String text = String.join(":", userIds) + "$$" + String.join(":", groupIds);
        return text;
    }

    @Override
    public Future<Optional<String>> getOrCreateNewShare(final Set<String> userIds, final Set<String> groupIds) throws Exception {
        if (userIds.isEmpty() && groupIds.isEmpty()) {
            return Future.succeededFuture(Optional.empty());
        }
        final String hash = this.text(new ArrayList<>(userIds), new ArrayList<>(groupIds));
        return Future.succeededFuture(Optional.of(hash));
    }

    @Override
    public Future<List<String>> findHashes(final UserInfos user) {
        //prepare ids
        final Set<String> userIds = new HashSet<>();
        userIds.add(user.getUserId());
        final Set<String> groupIds = new HashSet<>();
        groupIds.addAll(user.getGroupsIds());
        //create list
        final Set<String> ids = new HashSet<>();
        for (String u : userIds) {
            ids.add(ExplorerConfig.getVisibleByCreator(u));
            ids.add(ExplorerConfig.getVisibleByUser(u));
        }
        for (String u : groupIds) {
            ids.add(ExplorerConfig.getVisibleByGroup(u));
        }
        return Future.succeededFuture(new ArrayList<>(ids));
    }
}
