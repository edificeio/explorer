package com.opendigitaleducation.explorer.filters;

import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.user.UserInfos;

public class MoveFilter extends AbstractFilter {
    @Override
    protected Future<Boolean> doAuthorize(final HttpServerRequest request, final Binding binding, final UserInfos user) {
        final String id = request.params().get("id");
        return checkFolderById(user, id);
    }
}
