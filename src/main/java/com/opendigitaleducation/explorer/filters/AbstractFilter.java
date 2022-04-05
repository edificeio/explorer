package com.opendigitaleducation.explorer.filters;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.FolderSearchOperation;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.ResourceSearchOperation;
import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.StringUtils;

import java.util.Optional;

public abstract class AbstractFilter implements ResourcesProvider {

    protected static FolderService folderService;
    protected static ResourceService resourceService;
    protected Logger log = LoggerFactory.getLogger(getClass());

    public static void setFolderService(final FolderService folderService) {
        AbstractFilter.folderService = folderService;
    }

    public static void setResourceService(final ResourceService resourceService) {
        AbstractFilter.resourceService = resourceService;
    }

    protected Future<Boolean> checkFolderById(final UserInfos user, final String id){
        if(ExplorerConfig.ROOT_FOLDER_ID.equalsIgnoreCase(id)){
            return Future.succeededFuture(true);
        }else if(ExplorerConfig.BIN_FOLDER_ID.equalsIgnoreCase(id)){
            return Future.succeededFuture(true);
        }else if(StringUtils.isEmpty(id)){
            return Future.failedFuture("invalid.id");
        }else{
            final Promise<Boolean> promise = Promise.promise();
            final FolderSearchOperation search = new FolderSearchOperation().setId(id).setSearchEverywhere(true);
            folderService.count(user, Optional.empty(), search).onComplete(e -> {
                if (e.succeeded()) {
                    promise.complete(e.result().equals(1));
                } else {
                    promise.fail(e.cause());
                }
            });
            return promise.future();
        }
    }

    protected Future<Boolean> checkResourceById(final UserInfos user, final String id, final String application){
        if (!StringUtils.isEmpty(id) && !StringUtils.isEmpty(application)) {
            final Promise<Boolean> promise = Promise.promise();
            resourceService.count(user, application, new ResourceSearchOperation().setSearchEverywhere(true).setId(id)).onComplete(e -> {
                if (e.succeeded()) {
                    promise.complete(e.result().equals(1));
                } else {
                    promise.fail(e.cause());
                }
            });
            return promise.future();
        } else {
            return Future.succeededFuture(false);
        }
    }

    @Override
    public void authorize(final HttpServerRequest httpServerRequest, final Binding binding, final UserInfos userInfos, final Handler<Boolean> handler) {
        httpServerRequest.pause();
        doAuthorize(httpServerRequest, binding, userInfos).onComplete(e->{
           if(e.succeeded()){
               handler.handle(e.result());
               httpServerRequest.resume();
           }else{
               log.error("Failed to authorize: ", e.cause());
               handler.handle(false);
           }
        });
    }

    protected abstract Future<Boolean> doAuthorize(final HttpServerRequest httpServerRequest, final Binding binding, final UserInfos userInfos);

}
