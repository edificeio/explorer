package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import com.opendigitaleducation.explorer.folders.FolderExplorerCrudSql;
import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

public class MessageIngesterPostgres implements MessageIngester {
    private final FolderExplorerCrudSql sql;
    private final  MessageIngester ingester;

    public MessageIngesterPostgres(final FolderExplorerCrudSql sql,final  MessageIngester ingester) {
        this.ingester = ingester;
        this.sql = sql;
    }

    @Override
    public Future<IngestJob.IngestJobResult> ingest(final List<ExplorerMessageForIngest> messages) {
        final List<ExplorerMessageForIngest> upsertResources = new ArrayList<>();
        final List<ExplorerMessageForIngest> deleteResources = new ArrayList<>();
        for (final ExplorerMessageForIngest message : messages) {
            final ExplorerMessage.ExplorerAction a = ExplorerMessage.ExplorerAction.valueOf(message.getAction());
            switch (a) {
                case Delete:
                    if(!message.getApplication().equals(ExplorerConfig.FOLDER_APPLICATION)){
                        deleteResources.add(message);
                    }
                    break;
                case Upsert:
                    if(!message.getApplication().equals(ExplorerConfig.FOLDER_APPLICATION)){
                        upsertResources.add(message);
                    }
                    break;
                case Audience:
                    break;
            }
        }
        //create or delete resources in postgres
        final Future<List<ExplorerMessageForIngest>> beforeUpsertFuture = onUpsertResources(messages);
        final Future<List<ExplorerMessageForIngest>> beforeDeleteFuture = onDeleteResources(messages);
        return CompositeFuture.all(beforeUpsertFuture, beforeDeleteFuture).compose(all->{
            //ingest only resources created or deleted successfully in postgres
            final List<ExplorerMessageForIngest> toIngest = new ArrayList<>();
            toIngest.addAll(beforeUpsertFuture.result());
            toIngest.addAll(beforeDeleteFuture.result());
            return ingester.ingest(toIngest).map(ingestResult->{
                //add to failed all resources that cannot be deleted or created into postgres
                final List<ExplorerMessageForIngest> prepareFailed = new ArrayList<>(messages);
                prepareFailed.removeAll(toIngest);
                ingestResult.getFailed().addAll(prepareFailed);
                return ingestResult;
            }).compose(ingestResult->{
                //delete definitly all resources deleted from ES
                final List<ExplorerMessageForIngest> deleted = beforeDeleteFuture.result();
                final List<ExplorerMessageForIngest> deletedSuccess = deleted.stream().filter(del->{
                   final Optional<ExplorerMessageForIngest> found =  ingestResult.getSucceed().stream().filter(current->current.getResourceUniqueId().equals(del.getResourceUniqueId())).findFirst();
                    return found.isPresent();
                }).collect(Collectors.toList());
                return sql.deleteDefinitlyResources(deletedSuccess).map(ingestResult);
            });
        });
    }

    protected Future<List<ExplorerMessageForIngest>> onUpsertResources(final List<ExplorerMessageForIngest> messages){
        return sql.upsertResources(messages).map(resourcesSql->{
            final List<ExplorerMessageForIngest> backupSuccess = new ArrayList<>();
            for(final FolderExplorerCrudSql.ResouceSql entry : resourcesSql){
                final Optional<ExplorerMessageForIngest> found = messages.stream().filter(e->e.getResourceUniqueId().equals(entry.resourceUniqId)).findFirst();
                if(found.isPresent()){
                    final ExplorerMessageForIngest mess = found.get();
                    mess.setPredictibleId(entry.id.toString());
                    final Set<String> folderIds = new HashSet<>();
                    for(final FolderExplorerCrudSql.FolderSql folder : entry.folders){
                        //TODO use folderIds and rootUserIds do not create root folder
                        if(folder)
                            //TODO how to manage visibleBy / root folders (for shared...) / non root folders
                    }
                    mess.getMessage().put("folderIds", )
                }
                value.setPredictibleId(value.toString());
                backupSuccess.add(value);
            }
            return backupSuccess;
        });
    }

    protected Future<List<ExplorerMessageForIngest>> onDeleteResources(final List<ExplorerMessageForIngest> messages){
        return sql.deleteTemporarlyResources(messages).map(mapWithId->{
            final List<ExplorerMessageForIngest> deleteSuccess = new ArrayList<>();
            for(final Map.Entry<Integer, ExplorerMessage> entry : mapWithId.entrySet()){
                final ExplorerMessageForIngest value = (ExplorerMessageForIngest)entry.getValue();
                value.setPredictibleId(value.toString());
                deleteSuccess.add(value);
            }
            return deleteSuccess;
        });
    }

    @Override
    public Future<JsonObject> getMetrics() {
        return Future.succeededFuture(new JsonObject());
    }

}
