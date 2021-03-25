package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface FolderService {
    String SUCCESS_FIELD = "_success";
    String ERROR_FIELD = "_error";
    String ROOT_FOLDER_ID = "root";
    String DEFAULT_FOLDER_INDEX = "explorer_folder";

    //TODO fetch by application...
    Future<JsonArray> fetch(final UserInfos creator, final Optional<String> parentId);

    Future<String> create(final UserInfos creator, final JsonObject folder);

    Future<JsonObject> update(final UserInfos creator, final String id, final JsonObject folder);

    Future<List<JsonObject>> delete(final UserInfos creator, final Set<String> ids);

    Future<JsonObject> move(final UserInfos user, final JsonObject document, final Optional<String> source, final Optional<String> dest);

    Future<List<JsonObject>> create(final UserInfos creator, final List<JsonObject> folder);
}
