package com.opendigitaleducation.explorer.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Optional;


public interface FolderService {
    String TRASH_FOLDER_ID = "trash";
    String ROOT_FOLDER_ID = "root";
    String DEFAULT_FOLDER_INDEX = "explorer_folder";
    Future<JsonArray> fetch(final UserInfos creator, final Optional<String> parentId);
    Future<String> create(final UserInfos creator, final JsonObject folder);
    Future<List<JsonObject>> create(final UserInfos creator, final List<JsonObject> folder);
}
