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
import com.opendigitaleducation.explorer.ingest.IngestJob;
import com.opendigitaleducation.explorer.ingest.MessageIngester;
import com.opendigitaleducation.explorer.ingest.MessageReader;
import com.opendigitaleducation.explorer.postgres.PostgresClient;
import com.opendigitaleducation.explorer.redis.RedisClient;
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
    private IngestJob job;
    @Override
    public void start() throws Exception {
        super.start();
        //TODO start ingestjob in worker?
        //TODO move config to infra (or reuse)
        //TODO create ES mapping and check why folders not diplsaying
        final JsonObject elastic = config.getJsonObject("elastic");
        final JsonArray esUri = elastic.getJsonArray("uris");
        final List<URI> uriList = new ArrayList<>();
        for ( int i = 0 ; i < esUri.size() ; i++) {
            final Object uri = esUri.getValue(i);
            if(uri instanceof String) {
                uriList.add(new URI(uri.toString()));
            }else{
                throw new Exception("Bad uri for elastic search: "+ uri);
            }
        }
        final JsonObject postgresqlConfig = config.getJsonObject("postgres");
        //end move to infra
        final String esIndex = elastic.getString("index", "explorer");
        final URI[] uris = uriList.toArray(new URI[uriList.size()]);
        final ElasticClientManager elasticClientManager = new ElasticClientManager(vertx, uris);
        final PostgresClient postgresClient = new PostgresClient(vertx, postgresqlConfig);
        final ShareTableManager shareTableManager = new PostgresShareTableManager(postgresClient);
        final FolderService folderService = new FolderServiceElastic(elasticClientManager, esIndex);
        final ResourceService resourceService = new ResourceServiceElastic(elasticClientManager, shareTableManager, esIndex);
        final ExplorerController explorerController = new ExplorerController(folderService, resourceService);
        addController(explorerController);
        ResourceFilter.setResourceService(resourceService);
        FolderFilter.setFolderService(folderService);
        final JsonObject redisConfig = config.getJsonObject("redisConfig");
        final JsonObject ingestConfig = config.getJsonObject("ingest");
        final RedisClient redisClient = new RedisClient(vertx , redisConfig);
        final MessageReader reader = MessageReader.redis(redisClient, ingestConfig);
        final MessageIngester ingester = MessageIngester.elastic(resourceService);
        job = new IngestJob(vertx, reader, ingester, ingestConfig);
        job.start();
    }

}
