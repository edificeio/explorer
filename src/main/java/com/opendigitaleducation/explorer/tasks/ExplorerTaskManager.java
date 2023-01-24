package com.opendigitaleducation.explorer.tasks;

import com.opendigitaleducation.explorer.Explorer;
import com.opendigitaleducation.explorer.ExplorerConfig;
import fr.wseduc.cron.CronTrigger;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.postgres.IPostgresClient;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ExplorerTaskManager {
    private final List<CronTrigger> triggers = new ArrayList<>();
    public ExplorerTaskManager start(final Vertx vertx, final JsonObject config, final IPostgresClient pgClient) throws ParseException {
        if(config.getBoolean(Explorer.DELETE_FOLDER_CONFIG, Explorer.DELETE_FOLDER_CONFIG_DEFAULT)){
            final String cleanFolderCron = config.getString(Explorer.CLEAN_FOLDER_CRON_CONFIG, "0 0 5 * * ? *");
            triggers.add(new CronTrigger(vertx, cleanFolderCron).schedule(new CleanFolderTask(pgClient)));
        }
        return this;
    }

    public ExplorerTaskManager stop(){
        for(final CronTrigger trigger : this.triggers){
            trigger.cancel();
        }
        this.triggers.clear();
        return this;
    }
}
