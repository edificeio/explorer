package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import com.opendigitaleducation.explorer.folders.ResourceExplorerDbSql;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.postgres.PostgresClient;
import org.entcore.common.user.UserInfos;

import java.util.*;
import java.util.stream.Collectors;

public class MessageIngesterPostgres implements MessageIngester {
    private final ResourceExplorerDbSql sql;
    private final FolderExplorerDbSql folderSql;
    private final MessageIngester ingester;

    public MessageIngesterPostgres(final PostgresClient sql, final MessageIngester ingester) {
        this.ingester = ingester;
        this.sql = new ResourceExplorerDbSql(sql);
        this.folderSql = new FolderExplorerDbSql(sql);
    }

    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture(new IngestJob.IngestJobResult(new ArrayList<>(), new ArrayList<>()));
        }
        final List<ExplorerMessageForIngest> deleteFolders = new ArrayList<>();
        final List<ExplorerMessageForIngest> upsertFolders = new ArrayList<>();
        final List<ExplorerMessageForIngest> upsertResources = new ArrayList<>();
        final List<ExplorerMessageForIngest> deleteResources = new ArrayList<>();
        for (final ExplorerMessageForIngest message : messages) {
            final ExplorerMessage.ExplorerAction a = ExplorerMessage.ExplorerAction.valueOf(message.getAction());
            switch (a) {
                case Delete:
                    if (message.getResourceType().equals(ExplorerConfig.FOLDER_TYPE)) {
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
                case Audience:
                    break;
            }
        }
        //create or delete resources in postgres
        return migrate(upsertFolders, upsertResources).compose(migrate -> {
            final Future<List<ExplorerMessageForIngest>> beforeUpsertFolderFuture = onUpsertFolders(upsertFolders);
            final Future<List<ExplorerMessageForIngest>> beforeUpsertFuture = onUpsertResources(upsertResources);
            final Future<List<ExplorerMessageForIngest>> beforeDeleteFuture = onDeleteResources(deleteResources);
            return CompositeFuture.all(beforeUpsertFuture, beforeDeleteFuture, beforeUpsertFolderFuture).compose(all -> {
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
                    ingestResult.getFailed().addAll(prepareFailed);
                    return ingestResult;
                }).compose(ingestResult -> {
                    //delete definitly all resources deleted from ES
                    final List<ExplorerMessageForIngest> deleted = beforeDeleteFuture.result();
                    final List<ExplorerMessageForIngest> deletedSuccess = deleted.stream().filter(del -> {
                        final Optional<ExplorerMessageForIngest> found = ingestResult.getSucceed().stream().filter(current -> current.getResourceUniqueId().equals(del.getResourceUniqueId())).findFirst();
                        return found.isPresent();
                    }).collect(Collectors.toList());
                    return sql.deleteDefinitlyResources(deletedSuccess).map(ingestResult);
                });
            });
        });
    }

    protected Future<List<ExplorerMessageForIngest>> onUpsertResources(final List<ExplorerMessageForIngest> messages) {
        if (messages.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        return sql.upsertResources(messages).map(resourcesSql -> {
            final List<ExplorerMessageForIngest> backupSuccess = new ArrayList<>();
            for (final ResourceExplorerDbSql.ResouceSql resSql : resourcesSql) {
                final Optional<ExplorerMessageForIngest> found = messages.stream().filter(e -> e.getResourceUniqueId().equals(resSql.resourceUniqId)).findFirst();
                if (found.isPresent()) {
                    final ExplorerMessageForIngest mess = found.get();
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
                    //set visible
                    final Set<String> visibleBy = new HashSet<>();
                    visibleBy.add(ExplorerConfig.getCreatorRight(resSql.creatorId));
                    //get rights
                    final Optional<String> contribRights = ExplorerConfig.getInstance().getContribRightForApp(mess.getApplication());
                    final Optional<String> manageRights = ExplorerConfig.getInstance().getManageRightForApp(mess.getApplication());
                    for (final Map.Entry<String, Set<String>> entry : resSql.getRightsByUser().entrySet()) {
                        final String userId = entry.getKey();
                        final Set<String> rights = entry.getValue();
                        visibleBy.add(ExplorerConfig.getReadByUser(userId));
                        if(contribRights.isPresent() && rights.contains(contribRights.get())){
                            visibleBy.add(ExplorerConfig.getContribByUser(userId));
                        }
                        if(manageRights.isPresent() && rights.contains(manageRights.get())){
                            visibleBy.add(ExplorerConfig.getManageByUser(userId));
                        }
                    }
                    for (final Map.Entry<String, Set<String>> entry : resSql.getRightsForGroup().entrySet()) {
                        final String groupId = entry.getKey();
                        final Set<String> rights = entry.getValue();
                        visibleBy.add(ExplorerConfig.getReadByGroup(groupId));
                        if(contribRights.isPresent() && rights.contains(contribRights.get())){
                            visibleBy.add(ExplorerConfig.getContribByGroup(groupId));
                        }
                        if(manageRights.isPresent() && rights.contains(manageRights.get())){
                            visibleBy.add(ExplorerConfig.getManageByGroup(groupId));
                        }
                    }
                    mess.getMessage().put("rights", new JsonArray(new ArrayList(visibleBy)));
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
        final List<ExplorerMessageForIngest> toMigrate = upsertFolders.stream().filter(message -> {
            return message.getMigrationFlag();
        }).collect(Collectors.toList());
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
                        final ExplorerMessage mess = ExplorerMessage.upsert(r.entId, creator, false);
                        mess.withType(r.application, r.resourceType);
                        upsertResources.add(new ExplorerMessageForIngest(mess));
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
        final Set<Integer> parentIds = overrides.stream().filter(e -> e.containsKey("parentId")).map(e -> {
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
                final FolderExplorerDbSql.FolderRelationship relation = relations.get(id);
                final FolderExplorerDbSql.FolderAncestor ancestor = ancestors.getOrDefault(id, new FolderExplorerDbSql.FolderAncestor(id, new ArrayList<>()));
                //add children ids and ancestors (reuse existing override)
                final JsonObject override = message.getOverride();
                override.put("childrenIds", new JsonArray(relation.childrenIds));
                if (relation.parentId.isPresent()) {
                    override.put("parentId", relation.parentId.get());
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
                final ExplorerMessage mess = ExplorerMessage.upsert(parentIdStr, new UserInfos(), false).withType(ExplorerConfig.FOLDER_APPLICATION, ExplorerConfig.FOLDER_TYPE);
                final ExplorerMessageForIngest message = new ExplorerMessageForIngest(mess);
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

    @Override
    public Future<JsonObject> getMetrics() {
        return (ingester.getMetrics());
    }

}
