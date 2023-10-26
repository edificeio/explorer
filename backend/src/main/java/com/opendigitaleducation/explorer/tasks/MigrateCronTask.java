/*
 * Copyright © "Open Digital Education", 2016
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 */

package com.opendigitaleducation.explorer.tasks;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.services.ResourceService;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import static java.util.Collections.emptySet;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.to.ExplorerReindexResourcesRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MigrateCronTask implements Handler<Long> {

    private static final Logger log = LoggerFactory.getLogger(MigrateCronTask.class);
    private final Set<String> applications;
    private final boolean dropBefore;
    private final boolean migrateOldFolder;
    private final boolean migrateNewFolder;
    private final ResourceService resourceService;
    private final Vertx vertx;
    private int migrationCounter = 0;
    private final Set<String> states;

    public MigrateCronTask(final Vertx vertx, final ResourceService resourceService, final Set<String> applications,
                           final boolean dropBefore, final boolean migrateOldFolder, final boolean migrateNewFolder,
                           final Set<String> states) {
        this.vertx = vertx;
        this.resourceService = resourceService;
        this.applications = applications;
        this.dropBefore = dropBefore;
        this.migrateOldFolder = migrateOldFolder;
        this.migrateNewFolder = migrateNewFolder;
        this.states = states;
    }

    public Future<JsonArray> run(){
        final List<Future<IExplorerPluginClient.IndexResponse>> futures = new ArrayList<>();
        this.migrationCounter++;
        if(this.migrateNewFolder){
            final String application = ExplorerConfig.FOLDER_APPLICATION;
            log.info(String.format("[Migrate Cron] Starting migration taskId=%s application=%s", migrationCounter, application));
            final Future<Void> dropFuture = this.dropBefore ? resourceService.dropMapping(application).compose(e -> {
                return resourceService.initMapping(application);
            }) : Future.succeededFuture();
            final Future<IExplorerPluginClient.IndexResponse> future = dropFuture.compose(e -> {
                final IExplorerPluginClient client = IExplorerPluginClient.withBus(vertx, application);
                return client.reindex(null, new ExplorerReindexResourcesRequest(null, null, emptySet(), this.migrateOldFolder, emptySet(), this.states));
            });
            futures.add(future);
            future.onComplete(res -> {
                if (res.succeeded()) {
                    log.info(String.format("[Migrate Cron] Finished migration taskId=%s application=%s status=ok", migrationCounter, application));
                } else {
                    log.error(String.format("[Migrate Cron] Finished migration taskId=%s application=%s status=failed", migrationCounter, application), res.cause());
                }
            });
        }
        for (final String application : applications) {
            log.info(String.format("[Migrate Cron] Starting migration taskId=%s application=%s", migrationCounter, application));
            final Future<Void> dropFuture = this.dropBefore ? resourceService.dropMapping(application).compose(e -> {
                return resourceService.initMapping(application);
            }) : Future.succeededFuture();
            final Future<IExplorerPluginClient.IndexResponse> future = dropFuture.compose(e -> {
                final IExplorerPluginClient client = IExplorerPluginClient.withBus(vertx, application);
                return client.reindex(
                        null,
                        new ExplorerReindexResourcesRequest(null, null, emptySet(), this.migrateOldFolder, emptySet(), this.states)
                );
            });
            futures.add(future);
            future.onComplete(res -> {
                if (res.succeeded()) {
                    log.info(String.format("[Migrate Cron] Finished migration taskId=%s application=%s status=ok", migrationCounter, application));
                } else {
                    log.error(String.format("[Migrate Cron] Finished migration taskId=%s application=%s status=failed", migrationCounter, application), res.cause());
                }
            });
        }
        return CompositeFuture.all(new ArrayList<>(futures)).map(onFinish ->{
            final List<IExplorerPluginClient.IndexResponse> results = onFinish.list();
            return new JsonArray(results.stream().map(e -> e.toJson()).collect(Collectors.toList()));
        });
    }

    @Override
    public void handle(Long event) {
        this.run();
    }
}
