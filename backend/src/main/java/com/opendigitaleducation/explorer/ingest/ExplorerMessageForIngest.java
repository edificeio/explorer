package com.opendigitaleducation.explorer.ingest;

import com.opendigitaleducation.explorer.ExplorerConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.explorer.ExplorerMessage;
import org.entcore.common.share.ShareRoles;

import java.util.Optional;
import java.util.stream.Collectors;

public class ExplorerMessageForIngest extends ExplorerMessage {
    public static final String ATTEMPT_COUNT = "attempt_count";
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

    public long getVersion() {
        if(getMessage().getValue("version") == null){
            return 0l;
        }
        return getMessage().getLong("version",0l);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ExplorerMessageForIngest{");
        sb.append("idQueue=").append(idQueue);
        sb.append(", error='").append(error).append('\'');
        sb.append(", errorDetails='").append(errorDetails).append('\'');
        sb.append(", predictibleId=").append(predictibleId);
        sb.append(", metadata=").append(metadata);
        sb.append('}').append(super.toString());
        return sb.toString();
    }

    public boolean isSynthetic() {
        return !idQueue.isPresent();
    }

    public boolean isTrashed(){
        return this.getMessage().getBoolean("trashed", false);
    }

    public boolean isTrashedBy(String userId) {
        return this.getTrashedBy().contains(userId);
    }

    public boolean isFolderMessage(){
        return ExplorerConfig.FOLDER_TYPE.equals(getResourceType())
                && ExplorerConfig.FOLDER_TYPE.equals(getEntityType());
    }

    public int getAttemptCount() {
        return metadata.getInteger(ATTEMPT_COUNT, 0);
    }

    public void setAttemptCount(final int attemptCount) {
        metadata.put(ATTEMPT_COUNT, attemptCount);
    }

    public boolean hasRights(final boolean excludeCreator){
        final JsonArray rights = this.getRights();
        if(rights == null || rights.size() == 0){
            return false;
        }
        if(excludeCreator){
            final String creatorPrefix = ShareRoles.getSerializedForCreator("");
            final long count = rights.stream().filter(right -> !right.toString().startsWith(creatorPrefix)).count();
            return count > 0;
        }else{
            return rights.size() > 0;
        }
    }

    public boolean hasSubResources(){
        final JsonArray subresources = this.getSubresources();
        return subresources == null? false : subresources.size() > 0;
    }
}
