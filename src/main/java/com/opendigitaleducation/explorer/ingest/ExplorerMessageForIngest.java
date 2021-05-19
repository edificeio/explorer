package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.plugin.ExplorerMessage;
import io.vertx.core.json.JsonObject;

import java.util.Optional;

public class ExplorerMessageForIngest extends ExplorerMessage {
    private final String idQueue;
    private String error = "";
    private String errorDetails = "";
    private Optional<String> predictibleId = Optional.empty();
    private final JsonObject metadata = new JsonObject();

    public ExplorerMessageForIngest(final String resourceAction, final String idQueue, final String idResource, final JsonObject json) {
        super(idResource, ExplorerAction.valueOf(resourceAction), false);
        this.getMessage().mergeIn(json);
        this.idQueue = idQueue;
    }

    public void setPredictibleId(final String predictibleId) {
        this.predictibleId = Optional.of(predictibleId);
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

    public String getIdQueue() {
        return idQueue;
    }

    public JsonObject getMetadata() {
        return metadata;
    }
}
