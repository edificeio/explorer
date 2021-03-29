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
import com.opendigitaleducation.explorer.elastic.ElasticClientManager;
import com.opendigitaleducation.explorer.filters.FolderFilter;
import com.opendigitaleducation.explorer.filters.ResourceFilter;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.services.FolderService;
import com.opendigitaleducation.explorer.services.ResourceService;
import com.opendigitaleducation.explorer.services.impl.FolderServiceElastic;
import com.opendigitaleducation.explorer.services.impl.ResourceServiceElastic;
import com.opendigitaleducation.explorer.share.PostgresShareTableManager;
import com.opendigitaleducation.explorer.share.ShareTableManager;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.BaseServer;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class Explorer extends BaseServer {
    static final Logger logger = LoggerFactory.getLogger(Explorer.class);

    @Override
    public void start() throws Exception {
        super.start();
        //TODO start ingestjob in worker?
        //TODO move to infra
        final JsonObject elastic = config.getJsonObject("elastic");
        final JsonArray esUri = elastic.getJsonArray("uris");
        final List<URI> uriList = new ArrayList<>();
        for (final Object u : esUri) {
            uriList.add(new URI(u.toString()));
        }
        final JsonObject postgresqlConfig = config.getJsonObject("postgres");
        //end move to infra
        final String esIndex = elastic.getString("elastic", "explorer");
        final URI[] uris = (URI[]) uriList.toArray();
        final ElasticClientManager elasticClientManager = new ElasticClientManager(vertx, uris);
        final PostgresClient postgresClient = new PostgresClient(vertx, postgresqlConfig);
        final ShareTableManager shareTableManager = new PostgresShareTableManager(postgresClient);
        final FolderService folderService = new FolderServiceElastic(elasticClientManager, esIndex);
        final ResourceService resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, esIndex);
        final ExplorerController explorerController = new ExplorerController(folderService, resourceService);
        addController(explorerController);
        ResourceFilter.setResourceService(resourceService);
        FolderFilter.setFolderService(folderService);
    }

}
