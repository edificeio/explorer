package com.opendigitaleducation.explorer.filters;

import com.opendigitaleducation.explorer.services.FolderService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;

public class FolderFilter implements ResourcesProvider {

    private static FolderService folderService;

    public static void setFolderService(FolderService folderService) {
        FolderFilter.folderService = folderService;
    }

    @Override
    public void authorize(final HttpServerRequest request, final fr.wseduc.webutils.http.Binding binding, final UserInfos user, Handler<Boolean> handler) {
        final String id = request.params().get("id");
        if (!StringUtils.isEmpty(id)) {
            //TODO check right
            folderService.count(user, new FolderService.SearchOperation().setId(id)).onComplete(e -> {
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
