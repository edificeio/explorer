/*
 * Copyright © "Open Digital Education" (SAS “WebServices pour l’Education”), 2014
 *
 * This program is published by "Open Digital Education" (SAS “WebServices pour l’Education”).
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https: //opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.opendigitaleducation.explorer;


import com.opendigitaleducation.explorer.controllers.ExplorerController;
import com.opendigitaleducation.explorer.filters.AbstractFilter;
import com.opendigitaleducation.explorer.folders.FolderExplorerPlugin;
import com.opendigitaleducation.explorer.ingest.IngestJobWorker;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.DefaultShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import com.opendigitaleducation.explorer.tasks.ExplorerTaskManager;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.elasticsearch.ElasticClientManager;
import org.entcore.common.explorer.IExplorerPluginCommunication;
import org.entcore.common.http.BaseServer;
import org.entcore.common.postgres.IPostgresClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Explorer extends BaseServer {
    static Logger log = LoggerFactory.getLogger(Explorer.class);
    public static final boolean DELETE_FOLDER_CONFIG_DEFAULT = true;
    public static final String DELETE_FOLDER_CONFIG = "delete-folder-definitely";
    public static final String CLEAN_FOLDER_CRON_CONFIG = "clean-folder-cron";

    private Optional<ExplorerTaskManager> taskManager = Optional.empty();
    @Override
    public void start() throws Exception {
        log.info("Starting explorer...");
        super.start();
        final boolean runjobInWroker = config.getBoolean("worker-job", true);
        final boolean poolMode = config.getBoolean("postgres-pool-mode", true);
        final List<Future> futures = new ArrayList<>();
        //create postgres client
        log.info("Init postgres bus consumer...");
        if(runjobInWroker){
            IPostgresClient.initPostgresConsumer(vertx, config, poolMode);
        }
        final IPostgresClient postgresClient = IPostgresClient.create(vertx, config, false, poolMode);
        //create es client
        final ElasticClientManager elasticClientManager = ElasticClientManager.create(vertx, config);
        //set skip folder
        ExplorerConfig.getInstance().setSkipIndexOfTrashedFolders(config.getBoolean(DELETE_FOLDER_CONFIG, DELETE_FOLDER_CONFIG_DEFAULT));
        //init rights map
        ExplorerConfig.getInstance().setRightsByApplication(config.getJsonObject("applications", new JsonObject()));
        //init indexes
        ExplorerConfig.getInstance().setEsIndexes(config.getJsonObject("indexes", new JsonObject()));
        if(config.getBoolean("create-index", true)) {
            //create elastic schema if needed
            final Buffer mappingRes = vertx.fileSystem().readFileBlocking("es/mappingResource.json");
            //create custom indexs
            final Set<String> customApps = ExplorerConfig.getInstance().getApplications();
            for (final String app : customApps) {
                if (!ExplorerConfig.FOLDER_APPLICATION.equals(app)) {
                    final String index = ExplorerConfig.getInstance().getIndex(app);
                    final Future future = elasticClientManager.getClient().createMapping(index, mappingRes);
                    futures.add(future);
                    log.info("Creating ES Resource Mapping for application : " + app + " -> using index" + index);
                }
            }
            //create default index apps
            final Set<String> apps = config.getJsonObject("applications").fieldNames();
            for (final String app : apps) {
                if (!customApps.contains(app) && !ExplorerConfig.FOLDER_APPLICATION.equals(app)) {
                    final String index = ExplorerConfig.getInstance().getIndex(app);
                    final Future future = elasticClientManager.getClient().createMapping(index, mappingRes);
                    futures.add(future);
                    log.info("Creating ES Resource Mapping for application : " + app + " -> using index" + index);
                }
            }
            //create mapping folder
            final Buffer mappingFolder = vertx.fileSystem().readFileBlocking("es/mappingFolder.json");
            final String index = ExplorerConfig.getInstance().getIndex(ExplorerConfig.FOLDER_APPLICATION);
            final Future future = elasticClientManager.getClient().createMapping(index, mappingFolder);
            log.info("Creating ES Resource Folder using index" + index);
            futures.add(future);

            futures.add(createUpsertScript(config.getString("upsert-resource-script", "explorer-upsert-ressource"), elasticClientManager));
        }
        //create folder service
        final FolderExplorerPlugin folderPlugin = FolderExplorerPlugin.create();
        final FolderService folderService = new FolderServiceElastic(elasticClientManager, folderPlugin);
        //create resources service
        final ShareTableManager shareTableManager = new DefaultShareTableManager();
        final IExplorerPluginCommunication communication = folderPlugin.getCommunication();
        final ResourceService resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, communication, postgresClient);
        //create controller
        final ExplorerController explorerController = new ExplorerController(folderService, resourceService);
        addController(explorerController);
        //configure filter
        AbstractFilter.setResourceService(resourceService);
        AbstractFilter.setFolderService(folderService);
        //deploy ingest worker
        if(config.getBoolean("enable-job", true)){
            final Promise<String> onWorkerDeploy = Promise.promise();
            final DeploymentOptions dep = new DeploymentOptions().setConfig(config);
            if(runjobInWroker){
                dep.setWorker(true).setWorkerPoolName("ingestjob").setWorkerPoolSize(config.getInteger("pool-size", 1));
            }
            vertx.deployVerticle(new IngestJobWorker(),dep, onWorkerDeploy);
            futures.add(onWorkerDeploy.future());
            if(ExplorerConfig.getInstance().skipIndexOfTrashedFolders){
                this.taskManager = Optional.of(new ExplorerTaskManager().start(vertx, config, postgresClient));
            }
        }
        //call start promise
        CompositeFuture.all(futures).onComplete(e -> {
            log.info("Explorer application started -> " + e.succeeded());
            if(e.failed()){
                log.error("Explorer application failed to start", e.cause());
            }
        });
    }

    private Future createUpsertScript(final String scriptId, final ElasticClientManager elasticClientManager) {
        final Buffer upsertScript = vertx.fileSystem().readFileBlocking("es/upsertScript.json");
        final Future future = elasticClientManager.getClient().storeScript(scriptId, upsertScript);
        log.info("Creating Resource upsert script " + scriptId);
        return future;
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        log.info("Explorer application stopped ");
        this.taskManager.ifPresent(e -> e.stop());
    }
}
