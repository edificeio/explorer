package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.explorer.IdAndVersion;
import org.entcore.common.postgres.IPostgresClient;
import org.entcore.common.share.ShareModel;
import org.entcore.common.user.UserInfos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;

public class MessageIngesterPostgres implements MessageIngester {
    private static final Logger log = LoggerFactory.getLogger(MessageIngesterPostgres.class);
    private final ResourceExplorerDbSql sql;
    private final FolderExplorerDbSql folderSql;
    private final MessageIngester ingester;
    private final IngestJobMetricsRecorder ingestJobMetricsRecorder;

    public MessageIngesterPostgres(final IPostgresClient sql, final MessageIngester ingester, final IngestJobMetricsRecorder ingestJobMetricsRecorder) {
        this.ingester = ingester;
        this.sql = new ResourceExplorerDbSql(sql);
        this.folderSql = new FolderExplorerDbSql(sql);
        this.ingestJobMetricsRecorder = ingestJobMetricsRecorder;
    }

    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages) {
        log.trace("[IngesterPostgres] start ingest");
        final long start = System.currentTimeMillis();
        if (messages.isEmpty()) {
            return Future.succeededFuture(new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>()));
        }
        final List<ExplorerMessageForIngest> deleteFolders = new ArrayList<>();
        final List<ExplorerMessageForIngest> upsertFolders = new ArrayList<>();
        final List<ExplorerMessageForIngest> upsertResources = new ArrayList<>();
        final List<ExplorerMessageForIngest> deleteResources = new ArrayList<>();
        final List<ExplorerMessageForIngest> muteResources = new ArrayList<>();
        for (final ExplorerMessageForIngest message : messages) {
            final ExplorerMessage.ExplorerAction a = ExplorerMessage.ExplorerAction.valueOf(message.getAction());
            final String resourceType = message.getResourceType();
            if(StringUtils.isBlank(resourceType)) {
                log.error("Message is missing resource type so we will not retry it : " + message);
                message.setAttemptCount(Integer.MAX_VALUE - 1);
            } else {
                switch (a) {
                    case Delete:
                        if (resourceType.equals(ExplorerConfig.FOLDER_TYPE)) {
                            deleteFolders.add(message);
                        } else {
                            deleteResources.add(message);
                        }
                        break;
                    case Upsert:
                        if (message.getResourceType().equals(ExplorerConfig.FOLDER_TYPE)) {
                            upsertFolders.add(message);
                        } else {
                            upsertResources.add(message);
                        }
                        break;
                    case Mute:
                        muteResources.add(message);
                        break;
                }
            }
        }
        //create or delete resources in postgres
        return migrate(upsertFolders, upsertResources).compose(migrate -> {
            final Future<List<ExplorerMessageForIngest>> beforeUpsertFolderFuture = onUpsertFolders(upsertFolders);
            final Future<List<ExplorerMessageForIngest>> beforeUpsertFuture = onUpsertResources(upsertResources);
            final Future<List<ExplorerMessageForIngest>> beforeDeleteFuture = onDeleteResources(deleteResources);
            final Future<List<ExplorerMessageForIngest>> beforeMuteFuture = onMuteResources(muteResources);
            return CompositeFuture.join(beforeUpsertFuture, beforeDeleteFuture, beforeUpsertFolderFuture, beforeMuteFuture).compose(all -> {
                recordDelay(messages, start);
                if(all.succeeded()) {
                    //ingest only resources created or deleted successfully in postgres
                    final List<ExplorerMessageForIngest> toIngest = new ArrayList<>();
                    //TODO on delete folders => update related resources?
                    toIngest.addAll(deleteFolders);
                    toIngest.addAll(beforeDeleteFuture.result());
                    toIngest.addAll(beforeUpsertFuture.result());
                    toIngest.addAll(beforeUpsertFolderFuture.result());
                    return ingester.ingest(toIngest).map(ingestResult -> {
                        //add to failed all resources that cannot be deleted or created into postgres
                        final List<ExplorerMessageForIngest> prepareFailed = new ArrayList<>(messages);
                        prepareFailed.removeAll(toIngest);
                        prepareFailed.removeAll(muteResources);
                        for (final ExplorerMessageForIngest failedMessage : prepareFailed) {
                            if (isBlank(failedMessage.getError()) && isBlank(failedMessage.getErrorDetails())) {
                                failedMessage.setError("psql.error");
                                failedMessage.setErrorDetails("resource cannot be deleted or created into postgres");
                            }
                        }
                        ingestResult.getFailed().addAll(prepareFailed);
                    ingestResult.getSucceed().addAll(beforeMuteFuture.result());
                        return ingestResult;
                    }).compose(ingestResult -> {
                    //delete definitely all resources deleted from ES
                        final List<ExplorerMessageForIngest> deleted = beforeDeleteFuture.result();
                        final List<ExplorerMessageForIngest> deletedSuccess = deleted.stream().filter(del -> {
                            final Optional<ExplorerMessageForIngest> found = ingestResult.getSucceed().stream().filter(current -> current.getResourceUniqueId().equals(del.getResourceUniqueId())).findFirst();
                            return found.isPresent();
                        }).collect(Collectors.toList());
                        return sql.deleteDefinitlyResources(deletedSuccess).map(ingestResult);
                    });
                } else {
                    log.warn("Error in PostgresIngester", all.cause());
                    final List<ExplorerMessageForIngest> failed = new ArrayList<>();
                    failed.addAll(populateError(upsertFolders, beforeUpsertFolderFuture));
                    failed.addAll(populateError(upsertResources, beforeUpsertFuture));
                    failed.addAll(populateError(deleteResources, beforeDeleteFuture));
                    final Set<String> processedErrors = failed.stream()
                            .map(m -> m.getId()).filter(s -> isNotBlank(s))
                            .collect(Collectors.toSet());
                    for (final ExplorerMessageForIngest message : messages) {
                        final String id = message.getId();
                        if(isBlank(id) || !processedErrors.contains(id)) {
                            message.setError("pg.error");
                            message.setErrorDetails("pg.unprocessed.error");
                            failed.add(message);
                        }
                    }
                    final IngestJob.IngestJobResult result = new IngestJob.IngestJobResult(
                            emptyList(),
                            failed);
                    return Future.failedFuture(all.cause());
                }
            });
        });
    }

    private List<ExplorerMessageForIngest> populateError(final List<ExplorerMessageForIngest> messages, final Future<List<ExplorerMessageForIngest>> futureResult) {
        if(futureResult.failed()) {
            final String cause = futureResult.cause().toString();
            for (final ExplorerMessageForIngest message : messages) {
                if(isBlank(message.getError())) {
                    message.setError("pg.error");
                }
                if(isBlank(message.getErrorDetails())) {
                    message.setErrorDetails(cause);
                }
            }
        }
        return messages;
    }

    private void recordDelay(final List<ExplorerMessageForIngest> messages, final long start) {
        final long delay = System.currentTimeMillis() - start;
        ingestJobMetricsRecorder.onIngestPostgresResult(delay / (messages.size() + 1));
    }

    private Future<List<ExplorerMessageForIngest>> onMuteResources(List<ExplorerMessageForIngest> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        return sql.muteResources(messages).map(resources -> {
            final Set<String> entIds = resources.stream().map(resource -> resource.entId).collect(Collectors.toSet());
            return messages.stream()
                    .filter(message -> entIds.contains(message.getId()))
                    .collect(Collectors.toList());
        });
    }

    protected Future<List<ExplorerMessageForIngest>> onUpsertResources(final List<ExplorerMessageForIngest> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        return sql.upsertResources(messages).map(resourcesSql -> {
            final List<ExplorerMessageForIngest> backupSuccess = new ArrayList<>();
            for (final ResourceExplorerDbSql.ResouceSql resSql : resourcesSql) {
                final List<ExplorerMessageForIngest> found = messages.stream().filter(e -> e.getResourceUniqueId().equals(resSql.resourceUniqId)).collect(Collectors.toList());
                for (final ExplorerMessageForIngest mess : found) {
                    //set predictible id
                    mess.setPredictibleId(resSql.id.toString());
                    //set folder ids
                    final Set<String> folderIds = new HashSet<>();
                    final Set<String> usersForFolderIds = new HashSet<>();
                    for (final ResourceExplorerDbSql.FolderSql folder : resSql.folders) {
                        folderIds.add(folder.id.toString());
                        usersForFolderIds.add(folder.userId);
                    }
                    mess.getMessage().put("folderIds", new JsonArray(new ArrayList(folderIds)));
                    mess.getMessage().put("usersForFolderIds", new JsonArray(new ArrayList(usersForFolderIds)));
                    if(resSql.shared != null && resSql.rights != null){
                        mess.withShared(resSql.shared, new ArrayList<>(resSql.rights.getList()));
                    }
                    if(resSql.creatorId != null && ! resSql.creatorId.isEmpty()){
                        mess.withCreatorId(resSql.creatorId);
                    }
                    //keep original id
                    final JsonObject override = new JsonObject();
                    override.put("assetId", mess.getId());
                    mess.withOverrideFields(override);
                    //add to success list
                    backupSuccess.add(mess);
                }
            }
            return backupSuccess;
        });
    }

    protected Future<List<ExplorerMessageForIngest>> onDeleteResources(final List<ExplorerMessageForIngest> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        return sql.deleteTemporarlyResources(messages).map(mapWithId -> {
            final List<ExplorerMessageForIngest> deleteSuccess = new ArrayList<>();
            for (final Map.Entry<Integer, ExplorerMessage> entry : mapWithId.entrySet()) {
                final ExplorerMessageForIngest value = (ExplorerMessageForIngest) entry.getValue();
                value.setPredictibleId(entry.getKey().toString());
                deleteSuccess.add(value);
            }
            return deleteSuccess;
        });
    }

    protected Future<Void> migrate(final List<ExplorerMessageForIngest> upsertFolders, final List<ExplorerMessageForIngest> upsertResources){
        //migrated folders
        final List<ExplorerMessageForIngest> toMigrate = upsertFolders.stream()
                .filter(ExplorerMessage::getMigrationFlag)
                .collect(Collectors.toList());
        //upsert resources before computing links
        final Future<List<ResourceExplorerDbSql.ResouceSql>> futureResources = sql.upsertResources(upsertResources);
        final  Future<FolderExplorerDbSql.FolderUpsertResult> futureFolders = folderSql.upsert(toMigrate);
        return CompositeFuture.all(futureFolders, futureResources).compose(resourceUpserted -> {
            final FolderExplorerDbSql.FolderUpsertResult upsertRes = futureFolders.result();
            final Map<String, JsonObject> folderByEntId = upsertRes.folderEntById;
            final Set<ResourceExplorerDbSql.ResouceSql> resourceUpdated = upsertRes.resourcesUpdated;
            if(toMigrate.isEmpty()){
                return Future.succeededFuture();
            }else{
                return folderSql.updateParentEnt().compose(updated->{
                    //update parent_id
                    for(final ExplorerMessageForIngest mess : toMigrate){
                        final String entId = mess.getId();
                        if(folderByEntId.containsKey(entId)){
                            final JsonObject saved = folderByEntId.get(entId);
                            mess.withForceId(saved.getValue("id").toString());
                            if(updated.containsKey(entId)){
                                final ExplorerMessageForIngest tmp = updated.get(entId);
                                mess.withParentId(tmp.getParentId().map(e-> Long.valueOf(e)));
                            }else{
                                final Object parentId = saved.getValue("parent_id");
                                final Optional<Object> parentOpt = Optional.ofNullable(parentId);
                                mess.withParentId(parentOpt.map(e-> Long.valueOf(e.toString())));
                            }
                        }
                    }
                    upsertFolders.addAll(updated.values());
                    //add resources upserted
                    for(final ResourceExplorerDbSql.ResouceSql r : resourceUpdated){
                        final UserInfos creator = new UserInfos();
                        creator.setUserId(r.creatorId);
                        final Optional<ExplorerMessageForIngest> found = upsertResources.stream().filter(e-> e.getId().equals(r.entId)).findFirst();
                        if(!found.isPresent()){
                            //use id because upsertResource already done
                            final ExplorerMessage mess = ExplorerMessage.upsert(
                                    new IdAndVersion(r.entId, r.version), creator, false,
                                    r.application, r.resourceType, r.resourceType);
                            mess.withVersion(System.currentTimeMillis()).withSkipCheckVersion(true);
                            // TODO JBER check version to set
                            upsertResources.add(new ExplorerMessageForIngest(mess).setPredictibleId(r.id.toString()));
                        }
                    }
                    return Future.succeededFuture();
                });
            }
        });
    }

    protected Future<List<ExplorerMessageForIngest>> onUpsertFolders(final List<ExplorerMessageForIngest> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        //get ancestors
        final List<JsonObject> overrides = messages.stream().map(e -> e.getOverride()).collect(Collectors.toList());
        //get parentIds
        final Set<Integer> ids = messages.stream().map(e -> Integer.valueOf(e.getId())).collect(Collectors.toSet());
        final Set<Integer> parentIds = overrides.stream().filter(e -> e!= null && e.containsKey("parentId") && !ExplorerConfig.ROOT_FOLDER_ID.equals(e.getValue("parentId"))).map(e -> {
            final String tmp = e.getValue("parentId").toString();
            return Integer.valueOf(tmp);
        }).collect(Collectors.toSet());
        final Set<Integer> idsAndParents = new HashSet<>();
        idsAndParents.addAll(ids);
        idsAndParents.addAll(parentIds);
        //get ancestors of each documents
        final Future<Map<String, FolderExplorerDbSql.FolderAncestor>> ancestorsF = folderSql.getAncestors(ids);
        // get parent/child relationship for folder and their parents
        final Future<Map<String, FolderExplorerDbSql.FolderRelationship>> relationsF = folderSql.getRelationships(idsAndParents);
        return CompositeFuture.all(ancestorsF, relationsF).map(e -> {
            final Map<String, FolderExplorerDbSql.FolderAncestor> ancestors = ancestorsF.result();
            final Map<String, FolderExplorerDbSql.FolderRelationship> relations = relationsF.result();
            //Transform all
            for (final ExplorerMessageForIngest message : messages) {
                final String id = message.getId();
                final Optional<FolderExplorerDbSql.FolderRelationship> relation = Optional.ofNullable(relations.get(id));
                final FolderExplorerDbSql.FolderAncestor ancestor = ancestors.getOrDefault(id, new FolderExplorerDbSql.FolderAncestor(id, new ArrayList<>()));
                //add children ids and ancestors (reuse existing override)
                final JsonObject override = Optional.ofNullable(message.getOverride()).orElse(new JsonObject());
                if(relation.isPresent()){
                    override.put("childrenIds", new JsonArray(relation.get().childrenIds));
                    if (relation.get().parentId.isPresent()) {
                        override.put("parentId", relation.get().parentId.get());
                    }
                }
                override.put("ancestors", new JsonArray(ancestor.ancestorIds));
                message.withOverrideFields(override);
                if (ancestor.application.isPresent()) {
                    message.withForceApplication(ancestor.application.get());
                }
                transformFolder(message);
            }
            //update parent (childrenIds)
            for (final Integer parentId : parentIds) {
                final String parentIdStr = parentId.toString();
                final Optional<ExplorerMessageForIngest> found = messages.stream().filter(m-> m.getId().equals(parentIdStr)).findFirst();
                final ExplorerMessageForIngest message = found.orElseGet(() -> {
                    // TODO JBER check if that is the right thing to do. Should we not fetch the folder information first.
                    // This seems to be why the test FolderServiceTest.shouldCreateFolderTree fails
                    // When version is set to System.currentTimeMillis() then it still fails and another one fails
                    final ExplorerMessage mess = ExplorerMessage.upsert(parentIdStr, new UserInfos(), false)
                            .withType(ExplorerConfig.FOLDER_APPLICATION, ExplorerConfig.FOLDER_TYPE, ExplorerConfig.FOLDER_TYPE)
                            .withVersion(System.currentTimeMillis()).withSkipCheckVersion(true);
                    return new ExplorerMessageForIngest(mess);
                });
                final JsonObject override = new JsonObject();
                //set childrenIds
                final FolderExplorerDbSql.FolderRelationship relation = relations.get(parentIdStr);
                override.put("childrenIds", new JsonArray(relation.childrenIds));
                if (relation.parentId.isPresent()) {
                    override.put("parentId", relation.parentId.get());
                }
                //recompute ancestors from child ancestors
                if (!relation.childrenIds.isEmpty()) {
                    //get child ancestors
                    final String child = relation.childrenIds.get(0);
                    final FolderExplorerDbSql.FolderAncestor ancestor = ancestors.getOrDefault(child, new FolderExplorerDbSql.FolderAncestor(child, new ArrayList<>()));
                    final List<String> parentAncestors = new ArrayList<>(ancestor.ancestorIds);
                    //remove self from child ancestors
                    parentAncestors.remove(parentIdStr);
                    override.put("ancestors", new JsonArray(parentAncestors));
                    //add parent to list of updated folders
                    message.withOverrideFields(override);
                    //update parent only if childrenids has changed
                    if (ancestor.application.isPresent()) {
                        message.withForceApplication(ancestor.application.get());
                    }
                    messages.add(transformFolder(message));
                }
            }
            return messages;
        });
    }

    protected ExplorerMessageForIngest transformFolder(final ExplorerMessageForIngest message) {
        final JsonObject override = message.getOverride();
        //set parent
        if (!override.containsKey("parentId")) {
            override.put("parentId", ExplorerConfig.ROOT_FOLDER_ID);
        }
        //prepend root ancestors
        final JsonArray ancestors = override.getJsonArray("ancestors", new JsonArray());
        if (!ancestors.contains(ExplorerConfig.ROOT_FOLDER_ID)) {
            //prepend root
            final JsonArray newAncestors = new JsonArray();
            newAncestors.add(ExplorerConfig.ROOT_FOLDER_ID);
            newAncestors.addAll(ancestors.copy());
            ancestors.clear();
            ancestors.addAll(newAncestors);
        }
        override.put("ancestors", ancestors);
        //END MOVE
        return message;
    }

}
