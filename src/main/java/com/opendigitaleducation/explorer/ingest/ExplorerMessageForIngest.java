package com.opendigitaleducation.explorer.ingest;

import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.Optional;

public class ExplorerMessageForIngest extends ExplorerMessage {
    private final Optional<String> idQueue;
    private String error = "";
    private String errorDetails = "";
    private Optional<String> predictibleId = Optional.empty();
    private final JsonObject metadata = new JsonObject();

    public ExplorerMessageForIngest(ExplorerMessage message){
        super(message.getId(), message.getAction(), message.getPriority());
        this.getMessage().mergeIn(message.getMessage());
        this.idQueue = Optional.empty();
    }

    public ExplorerMessageForIngest(final String resourceAction, final String idQueue, final String idResource, final JsonObject json) {
        super(idResource, ExplorerAction.valueOf(resourceAction), false);
        this.getMessage().mergeIn(json);
        this.idQueue = Optional.ofNullable(idQueue);
    }

    public ExplorerMessageForIngest setPredictibleId(final String predictibleId) {
        this.predictibleId = Optional.of(predictibleId);
        return this;
    }

    public Optional<String> getPredictibleId() {
        return predictibleId;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public String getError() {
        return error;
    }

    public Optional<String> getIdQueue() {
        return idQueue;
    }

    public JsonObject getMetadata() {
        return metadata;
    }
}
