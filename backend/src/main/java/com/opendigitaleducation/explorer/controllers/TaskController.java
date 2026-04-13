package com.opendigitaleducation.explorer.controllers;

import com.opendigitaleducation.explorer.tasks.CleanFolderTask;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TaskController extends BaseController {
	protected static final Logger log = LoggerFactory.getLogger(TaskController.class);

	final CleanFolderTask cleanFolderTask;

	public TaskController(CleanFolderTask cleanFolderTask) {
		this.cleanFolderTask = cleanFolderTask;
	}

	@Post("api/internal/clean-folder")
	public void cleanFolder() {
		log.info("Triggered folder cleanup task");
		cleanFolderTask.handle(0L);
		render(null, 202);
	}
}
