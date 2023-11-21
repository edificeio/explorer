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
import java.util.function.Function;
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
            return Future.succeededFuture(new IngestJob.IngestJobResult());
        }
        final List<ExplorerMessageWithParent> moveResources = new ArrayList<>();
        final List<ExplorerMessageForIngest> deleteFolders = new ArrayList<>();
        final List<ExplorerMessageForIngest> upsertFolders = new ArrayList<>();
        final List<ExplorerMessageForIngest> upsertResources = new ArrayList<>();
        final List<ExplorerMessageForIngest> deleteResources = new ArrayList<>();
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
                            // resource need to be moved
                            if(message.getParentId().isPresent()){
                                moveResources.add(new ExplorerMessageWithParent(message.getParentId().get(), message));
                            }
                            upsertResources.add(message);
                        }
                        break;
                }
            }
        }
        // first upsert resource
        final Future<List<ExplorerMessageForIngest>> upsertResourceFuture = onUpsertResources(upsertResources);
        return upsertResourceFuture.compose(upsertedResource -> {
            // then migrate folder: upsert resources before to compute links between folder and resources
            return migrate(upsertFolders, upsertResources);
        }).compose(migrate -> {
            // then upsert folder (not part of migration)
            final Future<List<ExplorerMessageForIngest>> upsertFolderFuture = onUpsertFolders(upsertFolders);
            // move resource after upsert => case of a resource created and moved atomically
            final Future<List<ExplorerMessageForIngest>> moveResourceFuture = onMoveResource(moveResources, upsertResourceFuture.result());
            final Future<List<ExplorerMessageForIngest>> deleteResourceFuture = onDeleteResources(deleteResources);
            return CompositeFuture.join(upsertResourceFuture, deleteResourceFuture, upsertFolderFuture, moveResourceFuture).compose(all -> {
                recordDelay(messages, start);
                if(all.succeeded()) {
                    //ingest only resources created or deleted successfully in postgres
                    final List<ExplorerMessageForIngest> toIngest = new ArrayList<>();
                    //TODO on delete folders => update related resources?
                    toIngest.addAll(deleteFolders);
                    toIngest.addAll(deleteResourceFuture.result());
                    toIngest.addAll(upsertResourceFuture.result());
                    toIngest.addAll(upsertFolderFuture.result());
                    toIngest.addAll(moveResourceFuture.result());
                    return ingester.ingest(toIngest).map(ingestResult -> {
                        //add to failed all resources that cannot be deleted or created into postgres
                        final List<ExplorerMessageForIngest> prepareFailed = new ArrayList<>(messages);
                        prepareFailed.removeAll(toIngest);
                        final List<ExplorerMessageForIngest> failedMessages = ingestResult.getFailed();
                        final List<ExplorerMessageForIngest> skippedMessages = ingestResult.getSkipped();
                        for (final ExplorerMessageForIngest failedMessage : prepareFailed) {
                            if(ExplorerMessage.ExplorerAction.Delete.name().equals(failedMessage.getAction())) {
                                log.warn("Cannot perform in postgres the action in " + failedMessage +". The target resource was probably already deleted or had never been indexed.");
                                // Add the failing message to the succeeded ones so it can be ack-ed in the reader
                                skippedMessages.add(failedMessage);
                                // And we don't add it to failedMessagess on purpose
                            } else if (isBlank(failedMessage.getError()) && isBlank(failedMessage.getErrorDetails())) {
                                failedMessage.setError("psql.error");
                                failedMessage.setErrorDetails("resource cannot be deleted or created into postgres");
                                failedMessages.add(failedMessage);
                            }
                        }
                        return new IngestJob.IngestJobResult(ingestResult.getSucceed(), failedMessages, skippedMessages);
                    }).compose(ingestResult -> {
                        //delete definitely all resources deleted from ES
                        final List<ExplorerMessageForIngest> deleted = deleteResourceFuture.result();
                        final List<ExplorerMessageForIngest> deletedSuccess = deleted.stream().filter(del -> {
                            final Optional<ExplorerMessageForIngest> found = ingestResult.getSucceed().stream().filter(current -> current.getResourceUniqueId().equals(del.getResourceUniqueId())).findFirst();
                            return found.isPresent();
                        }).collect(Collectors.toList());
                        return sql.deleteDefinitlyResources(deletedSuccess).map(ingestResult);
                    });
                } else {
                    log.warn("Error in PostgresIngester", all.cause());
                    final List<ExplorerMessageForIngest> failed = new ArrayList<>();
                    failed.addAll(populateError(upsertFolders, upsertFolderFuture));
                    failed.addAll(populateError(upsertResources, upsertResourceFuture));
                    failed.addAll(populateError(deleteResources, deleteResourceFuture));
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

    protected Future<List<ExplorerMessageForIngest>> onUpsertResources(final List<ExplorerMessageForIngest> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        return sql.upsertResources(messages).map(resourcesSql -> {
            return mapUpsertResourceToMessage(messages, resourcesSql);
        });
    }

    private List<ExplorerMessageForIngest> mapUpsertResourceToMessage(final List<ExplorerMessageForIngest> messages, final List<ResourceExplorerDbSql.ResouceSql> resourcesSql){
        final List<ExplorerMessageForIngest> backupSuccess = new ArrayList<>();
        for (final ResourceExplorerDbSql.ResouceSql resSql : resourcesSql) {
            final List<ExplorerMessageForIngest> found = messages.stream().filter(e -> e.getResourceUniqueId().equals(resSql.resourceUniqId)).collect(Collectors.toList());
            for (final ExplorerMessageForIngest mess : found) {
                updateMessageFromResourceSql(mess, resSql);
                //add to success list
                backupSuccess.add(mess);
            }
        }
        return backupSuccess;
    }

    private void updateMessageFromResourceSql(final ExplorerMessageForIngest mess, final ResourceExplorerDbSql.ResouceSql resSql ){
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
        if(resSql.rights != null){
            mess.withShared(new JsonArray(), new ArrayList<>(resSql.rights.getList()));
        }
        if(resSql.creatorId != null && ! resSql.creatorId.isEmpty()){
            mess.withCreatorId(resSql.creatorId);
        }
        //keep original id
        final JsonObject override = new JsonObject();
        override.put("assetId", mess.getId());
        mess.withOverrideFields(override);
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
        //
        if(toMigrate.isEmpty()){
            return Future.succeededFuture();
        }
        final  Future<FolderExplorerDbSql.FolderUpsertResult> futureFolders = folderSql.upsert(toMigrate);
        return futureFolders.compose(p -> {
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
                        final List<ExplorerMessageForIngest> found = upsertResources.stream().filter(e-> e.getId().equals(r.entId)).collect(Collectors.toList());
                        // get resource to be updated or create new message to update resource
                        if(found.isEmpty()){
                            //use id because upsertResource already done
                            final ExplorerMessage mess = ExplorerMessage.upsert(
                                    new IdAndVersion(r.entId, r.version), creator, false,
                                    r.application, r.resourceType, r.resourceType);
                            mess.withVersion(System.currentTimeMillis()).withSkipCheckVersion(true);
                            // TODO JBER check version to set
                            final ExplorerMessageForIngest messForIngest = new ExplorerMessageForIngest(mess).setPredictibleId(r.id.toString());
                            upsertResources.add(messForIngest);
                            found.add(messForIngest);
                        }
                        // update all messages
                        for(final ExplorerMessageForIngest foundMessage: found){
                            updateMessageFromResourceSql(foundMessage, r);
                        }
                    }
                    return Future.succeededFuture();
                });
            }
        });
    }

    protected Future<List<ExplorerMessageForIngest>> onMoveResource(final List<ExplorerMessageWithParent> toMoveList, final List<ExplorerMessageForIngest> upsertResources) {
        if (toMoveList.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        // get resource unique ID
        final Map<String, ExplorerMessageForIngest> upsertedById = upsertResources.stream().collect(Collectors.toMap(ExplorerMessageForIngest::getId, Function.identity()));
        final List<ResourceExplorerDbSql.ResourceLink> links = toMoveList.stream().map(toMove -> {
            final Optional<String> predictibleId = upsertedById.get(toMove.message.getId()).getPredictibleId();
            final Integer folderId = Integer.valueOf(toMove.parentId);
            final String updaterId = toMove.message.getUpdaterId();
            // predictible id has been setted on upsert
            return new ResourceExplorerDbSql.ResourceLink(folderId, Long.valueOf(predictibleId.get()), updaterId);
        }).collect(Collectors.toList());
        return sql.moveTo(links).map(resourcesSql -> {
            final List<ExplorerMessageForIngest> beforeMap = toMoveList.stream().map(e -> e.message).collect(Collectors.toList());
            final List<ExplorerMessageForIngest> mapped = mapUpsertResourceToMessage(beforeMap, resourcesSql);
            return mapped;
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
                final Optional<ExplorerMessageForIngest> found = messages.stream().filter(m-> m.getId().equals(parentIdStr)).reduce((first, second) -> second);
                final ExplorerMessageForIngest message = found.orElseGet(() -> {
                    //reuse application from child if absent
                    final String application = relations.values().stream().filter(relation -> relation.parentId.orElse("").equals(parentIdStr)).map(rel -> rel.application).findAny().orElse(ExplorerConfig.FOLDER_APPLICATION);
                    // TODO JBER check if that is the right thing to do. Should we not fetch the folder information first.
                    // This seems to be why the test FolderServiceTest.shouldCreateFolderTree fails
                    // When version is set to System.currentTimeMillis() then it still fails and another one fails
                    final ExplorerMessage mess = ExplorerMessage.upsert(new IdAndVersion(parentIdStr, System.currentTimeMillis()), new UserInfos(), false, application, ExplorerConfig.FOLDER_TYPE, ExplorerConfig.FOLDER_TYPE)
                            .withSkipCheckVersion(true);
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

    class ExplorerMessageWithParent{
        final String parentId;
        final ExplorerMessageForIngest message;

        public ExplorerMessageWithParent(String parentId, ExplorerMessageForIngest message) {
            this.parentId = parentId;
            this.message = message;
        }
    }

}
