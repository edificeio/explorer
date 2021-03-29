package com.opendigitaleducation.explorer.filters;

import com.opendigitaleducation.explorer.services.ResourceService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;

public class ResourceFilter implements ResourcesProvider {

    private static ResourceService resourceService;

    public static void setResourceService(ResourceService resourceService) {
        ResourceFilter.resourceService = resourceService;
    }

    @Override
    public void authorize(final HttpServerRequest request, final fr.wseduc.webutils.http.Binding binding, final UserInfos user, Handler<Boolean> handler) {
        final String id = request.params().get("id");
        final String application = request.params().get("application");
        if (!StringUtils.isEmpty(id) && !StringUtils.isEmpty(application)) {
            //TODO check the right
            resourceService.count(user, application, new ResourceService.SearchOperation().setSearchEverywhere(true).setId(id)).onComplete(e -> {
                if (e.succeeded()) {
                    handler.handle(e.result().equals(1));
                } else {
                    handler.handle(false);
                }
            });
        } else {
            handler.handle(false);
        }
    }
}
