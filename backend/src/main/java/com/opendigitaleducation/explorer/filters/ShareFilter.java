package com.opendigitaleducation.explorer.filters;

import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.user.UserInfos;

public class ShareFilter extends AbstractFilter {
    @Override
    protected Future<Boolean> doAuthorize(final HttpServerRequest request, final Binding binding, final UserInfos user) {
        final String id = request.params().get("resourceId");
        final String application = request.params().get("application");
        return checkResourceById(user, id, application);
    }
}
