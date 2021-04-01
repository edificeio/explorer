package com.opendigitaleducation.explorer.plugin;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public abstract class ExplorerPluginResourceCrud extends ExplorerPluginResource {
    protected final ExplorerResourceCrud resourceCrud;
    protected ExplorerPluginResourceCrud(final ExplorerPluginCommunication communication, final ExplorerResourceCrud crud) {
        super(communication);
        this.resourceCrud = crud;
    }

    @Override
    protected String getIdForModel(final JsonObject json) {
        return resourceCrud.getIdForModel(json);
    }

    @Override
    protected void setIdForModel(final JsonObject json, final String id) { resourceCrud.setIdForModel(json,id); }

    @Override
    protected UserInfos getCreatorForModel(final JsonObject json) { return this.resourceCrud.getCreatorForModel(json); }

    @Override
    protected void doFetchForIndex(final ExplorerStream<JsonObject> stream, final Optional<Date> from, final Optional<Date> to) {
        this.resourceCrud.fetchByDate(stream, from, to);
    }

    @Override
    protected Future<List<String>> doCreate(final UserInfos user, final List<JsonObject> sources, final boolean isCopy) {
        return this.resourceCrud.createAll(user, sources);
    }

    @Override
    protected Future<List<Boolean>> doDelete(final UserInfos user, final List<String> ids) {
        return this.resourceCrud.deleteById(ids);
    }

    public ExplorerResourceCrud getResourceCrud() {
        return resourceCrud;
    }
}
