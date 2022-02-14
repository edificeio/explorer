package com.opendigitaleducation.explorer.share;

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

public class PostgresShareTableManager implements ShareTableManager {
    static final String ALGO_MD5 = "MD5";
    private final PostgresClientPool pgClient;

    public PostgresShareTableManager(PostgresClient pgClient) {
        this.pgClient = pgClient.getClientPool();
    }

    protected String text(final List<String> userIds, final List<String> groupIds) {
        userIds.sort(Comparator.comparing(String::toString));
        groupIds.sort(Comparator.comparing(String::toString));
        final String text = String.join(":", userIds) + "$$" + String.join(":", groupIds);
        return text;
    }

    protected String md5(final List<String> userIds, final List<String> groupIds) throws Exception {
        final String text = text(userIds, groupIds);
        return Md5.hash(text);
    }

    @Override
    public Future<Optional<String>> getOrCreateNewShare(final Set<String> userIds, final Set<String> groupIds) throws Exception {
        if (userIds.isEmpty() && groupIds.isEmpty()) {
            return Future.succeededFuture(Optional.empty());
        }
        final String hash = this.md5(new ArrayList<>(userIds), new ArrayList<>(groupIds));
        final String fetch = "SELECT * FROM explorer.share_subjects WHERE id=$1 AND hash_algorithm=$2 LIMIT 1";
        return pgClient.preparedQuery(fetch, Tuple.of(hash, ALGO_MD5)).compose(r -> {
            if (r.size() == 0) {
                return pgClient.transaction().compose(transaction -> {
                    final String query = "INSERT INTO explorer.share_subjects (id, hash_algorithm) VALUES ($1,$2) ";
                    final Tuple tuple = Tuple.of(hash, ALGO_MD5);
                    transaction.addPreparedQuery(query, tuple);
                    if (!groupIds.isEmpty()) {
                        final List<JsonObject> rows = groupIds.stream().map(id -> new JsonObject().put("share_subject_id", hash).put("id", id)).collect(Collectors.toList());
                        final String placeholder = PostgresClient.insertPlaceholders(rows, 1, "id", "share_subject_id");
                        final Tuple values = PostgresClient.insertValues(rows, Tuple.tuple(), "id", "share_subject_id");
                        final String queryGroups = String.format("INSERT INTO explorer.share_groups (id, share_subject_id) VALUES %s", placeholder);
                        transaction.addPreparedQuery(queryGroups, values);
                    }
                    if (!userIds.isEmpty()) {
                        final List<JsonObject> rows = userIds.stream().map(id -> new JsonObject().put("share_subject_id", hash).put("id", id)).collect(Collectors.toList());
                        final String placeholder = PostgresClient.insertPlaceholders(rows, 1, "id", "share_subject_id");
                        final Tuple values = PostgresClient.insertValues(rows, Tuple.tuple(), "id", "share_subject_id");
                        final String queryGroups = String.format("INSERT INTO explorer.share_users (id, share_subject_id) VALUES %s", placeholder);
                        transaction.addPreparedQuery(queryGroups, values);
                    }
                    return transaction.commit();
                });
            } else {
                return Future.succeededFuture();
            }
        }).map(e -> Optional.of(hash));
    }

    @Override
    public Future<List<String>> findHashes(final UserInfos user) {
        //prepare ids
        final Set<String> userIds = new HashSet<>();
        userIds.add(user.getUserId());
        final Set<String> groupIds = new HashSet<>();
        groupIds.addAll(user.getGroupsIds());
        //query
        final StringBuilder query = new StringBuilder();
        query.append(" SELECT s.id as id FROM explorer.share_subjects s INNER JOIN explorer.share_users su ON (s.id=su.share_subject_id) ");
        query.append(String.format(" WHERE su.id IN (%s) ", PostgresClient.inPlaceholder(userIds, 1)));
        if (!groupIds.isEmpty()) {
            query.append(" UNION ");
            query.append(" SELECT s.id as id FROM explorer.share_subjects s INNER JOIN explorer.share_groups sg ON (s.id=sg.share_subject_id) ");
            query.append(String.format(" WHERE sg.id IN (%s) ", PostgresClient.inPlaceholder(groupIds, userIds.size() + 1)));
        }
        //tuple
        final Tuple tuple = Tuple.tuple();
        for (String u : userIds) {
            tuple.addString(u);
        }
        for (String u : groupIds) {
            tuple.addString(u);
        }
        return pgClient.preparedQuery(query.toString(), tuple).map(res -> {
            final Set<String> ids = new HashSet<>();
            for (final Row row : res) {
                ids.add(row.getString("id"));
            }
            return new ArrayList<>(ids);
        });
    }
}
