package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Optional;

//TODO add log profile
//TODO add metrics
public interface ResourceService {

    Future<Void> init(final String application);

    Future<Void> dropAll(final String application);
    //TODO fetch by other criterias...
    Future<JsonArray> fetch(final UserInfos user, final String application, final SearchOperation operation);

    Future<FetchResult> fetchWithMeta(final UserInfos user, final String application, final SearchOperation operation);

    Future<Integer> count(final UserInfos user, final String application, final SearchOperation operation);

    Future<JsonObject> move(final UserInfos user, final String application, final JsonObject document, final Optional<Integer> dest);

    Future<JsonObject> share(final UserInfos user, final String application, final JsonObject document, final List<ShareOperation> operation) throws Exception;

    Future<List<JsonObject>> share(final UserInfos user, final String application, final List<JsonObject> documents, final List<ShareOperation> operation) throws Exception;

    void stopConsumer();

    class FetchResult{
        public final Long count;
        public final List<JsonObject> rows;

        public FetchResult(Long count, List<JsonObject> rows) {
            this.count = count;
            this.rows = rows;
        }
    }

    class ShareOperation {
        final String id;
        final boolean group;
        final JsonObject rights;

        public ShareOperation(String id, boolean group, JsonObject rights) {
            this.id = id;
            this.group = group;
            this.rights = rights;
        }

        public String getId() {
            return id;
        }

        public boolean isGroup() {
            return group;
        }

        public JsonObject getRights() {
            return rights;
        }

        public JsonObject toJsonRight() {
            return this.rights.copy().put(group ? "groupId" : "userId", id);
        }
    }

}
