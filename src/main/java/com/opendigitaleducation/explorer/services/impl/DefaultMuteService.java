package com.opendigitaleducation.explorer.services.impl;

import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import com.opendigitaleducation.explorer.services.MuteService;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.IdAndVersion;
import org.entcore.common.explorer.to.MuteRequest;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.entcore.common.mute.MuteService.FETCH_RESOURCE_MUTES_BY_ENTID_ADRESS;

public class DefaultMuteService implements MuteService {
    private static final Logger log = LoggerFactory.getLogger(DefaultMuteService.class);
    private final Vertx vertx;
    private final ResourceExplorerDbSql resourceDao;

    public DefaultMuteService(Vertx vertx, ResourceExplorerDbSql resourceDao) {
        this.vertx = vertx;
        this.resourceDao = resourceDao;
        this.startListening();
    }

    private void startListening() {
        final EventBus eb = this.vertx.eventBus();
        eb.consumer(FETCH_RESOURCE_MUTES_BY_ENTID_ADRESS, this::onFetchResourcesMutesByEntId);
    }


    private void onFetchResourcesMutesByEntId(final Message<String> message) {
        final String entId = message.body();
        this.resourceDao.getMutedByEntId(entId).onComplete(e -> {
            if(e.succeeded()) {
                message.reply(new JsonArray(new ArrayList<>(e.result())));
            } else {
                log.error("Could not fetch muters of resource " + entId, e.cause());
                message.fail(500, "fetch.muters.failed");
            }
        });
    }

    @Override
    public Future<Void> mute(final UserInfos userInfos, final MuteRequest muteRequest) {
        final Set<String> resourceIds = muteRequest.getResourceIds().stream().map(IdAndVersion::getId).collect(Collectors.toSet());
        return this.resourceDao.getModelByEntIds(resourceIds)
        .compose(resourceModels -> {
            // Group messages by application and resource type to send them all in one go
            final Map<String, List<ResourceExplorerDbSql.ResouceSql>> resourcesByApplicationAndType = resourceModels.stream().collect(Collectors.groupingBy(
                    resource -> resource.application + "_" + resource.resourceType
            ));
            final List<Future> muteStatusByAppAndType = resourcesByApplicationAndType.values().stream().map(resources -> {
                final ResourceExplorerDbSql.ResouceSql resource = resources.get(0);
                final String application = resource.application;
                final String resourceType = resource.resourceType;
                final IExplorerPluginClient client = IExplorerPluginClient.withBus(vertx, application, resourceType);
                // Call the client app to mute the resources
                return client.setMuteStatusByIds(userInfos, muteRequest.getResourceIds(), muteRequest.isMute());
            }).collect(Collectors.toList());
           return CompositeFuture.join(muteStatusByAppAndType).mapEmpty();
        });
    }

    // TODO JBER check rights. Any user can get the muters of a resource.
    @Override
    public Future<Set<String>> getMutedBy(final String id, final UserInfos userInfos) {
        return resourceDao.getMutedByEntId(id);
    }
}
