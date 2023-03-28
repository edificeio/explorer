package com.opendigitaleducation.explorer.tasks;


import com.opendigitaleducation.explorer.folders.FolderExplorerDbSql;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.postgres.IPostgresClient;

public class CleanFolderTask implements Handler<Long> {
    static Logger log = LoggerFactory.getLogger(CleanFolderTask.class);

    private final FolderExplorerDbSql helper;

    public CleanFolderTask(final IPostgresClient sql){
        this.helper = new FolderExplorerDbSql(sql);
    }

    @Override
    public void handle(Long event) {
        // delete folder where trashed is true
        log.info("Starting clean folder task...");
        helper.deleteTrashedFolderIds().onComplete(e -> {
            if(e.succeeded()){
                log.info("Finish clean folder task. count="+e.result().size());
            }else{
                log.error("Folder task failed :"+e.cause());
            }
        });
    }
}
