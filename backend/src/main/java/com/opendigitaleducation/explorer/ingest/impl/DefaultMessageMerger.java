package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MergeMessagesResult;
import com.opendigitaleducation.explorer.ingest.MessageMerger;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.NotImplementedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge UPSERT and delete messages of a resource based on its resourceUniqueId field.
 *
 * So far it does not handle Audience message.
 */
public class DefaultMessageMerger implements MessageMerger {

    static Logger log = LoggerFactory.getLogger(DefaultMessageMerger.class);

    @Override
    public String getId() {
        return "default";
    }

    @Override
    public MergeMessagesResult mergeMessages(final List<ExplorerMessageForIngest> messagesFromReader) {
        /*final Map<String, ExplorerMessageForIngest> messagesToTreat = new HashMap<>();
        final Map<String, List<ExplorerMessageForIngest>> messagesByResource = new HashMap<>();
        //final Map<String, Long> lastVersionByResource = new HashMap<>();
        // So far we don't need version as long as we can ensure that the order of the messages returned by the reader
        // is the chronological order
        for (final ExplorerMessageForIngest explorerMessageForIngest : messagesFromReader) {
            final String resourceUniqueId = explorerMessageForIngest.getResourceUniqueId();
            if(messagesByResource.containsKey(resourceUniqueId)) {
                final ExplorerMessageForIngest alreadyGeneratedMessage = messagesToTreat.get(resourceUniqueId);
                final ExplorerMessage.ExplorerAction currentAction = ExplorerMessage.ExplorerAction.valueOf(explorerMessageForIngest.getAction());
                final ExplorerMessage.ExplorerAction alreadyGeneratedAction = ExplorerMessage.ExplorerAction.valueOf(alreadyGeneratedMessage.getAction());
                switch (currentAction) {
                    case Delete:
                        messagesToTreat.put(resourceUniqueId, explorerMessageForIngest);
                        break;
                    case Upsert:
                        switch (alreadyGeneratedAction) {
                            case Delete:
                                log.error("An extremely weird error occurred : an upsert message appeared after a delete message");
                                break;
                            case Audience:
                                log.error("We do not know how to merge audience messages so far");
                                break;
                            case Upsert:
                                messagesToTreat.put(resourceUniqueId, mergeMessageIntoExistingOne(alreadyGeneratedMessage, explorerMessageForIngest));
                                break;
                        }
                        break;
                    case Audience:
                        log.error("We do not know how to merge audience messages so far");
                        break;
                }
            } else {
                messagesToTreat.put(resourceUniqueId, explorerMessageForIngest);
            }
            messagesByResource.compute(resourceUniqueId, (k, v) -> {
                final List<ExplorerMessageForIngest> ingests;
                if(v == null) {
                    ingests = new ArrayList<>();
                } else {
                    ingests = v;
                }
                ingests.add(explorerMessageForIngest);
                return ingests;
            });
        }
        return new MergeMessagesResult(new ArrayList<>(messagesToTreat.values()), messagesByResource);*/
        throw new NotImplementedException("not.working.anymore");
    }
    private ExplorerMessageForIngest mergeMessageIntoExistingOne(final ExplorerMessageForIngest oldMessage,
                                                                 final ExplorerMessageForIngest newMessage) {
        final boolean correctOrder = newMessage.getVersion() > oldMessage.getVersion();
        final ExplorerMessageForIngest mostRecentMessage = correctOrder ? newMessage : oldMessage;
        final ExplorerMessageForIngest merged = new ExplorerMessageForIngest(mostRecentMessage);
        final JsonArray oldMessageResources = oldMessage.getSubresources();
        final JsonArray newSubResources = newMessage.getSubresources();
        final JsonArray finalSubResources = new JsonArray();
        final Map<String, Long> versionPerResource = new HashMap<>();
        if(oldMessageResources != null) {
            finalSubResources.addAll(oldMessageResources);
            for (int i = 0; i < oldMessageResources.size(); i++) {
                final JsonObject subResource = oldMessageResources.getJsonObject(i);
                final String subResourceId = subResource.getString("id");
                final long subResourceVersion = subResource.getLong("version");
                versionPerResource.put(subResourceId, subResourceVersion);
            }
        }
        for (int i = 0; i < newSubResources.size(); i++) {
            final JsonObject subResource = newSubResources.getJsonObject(i);
            final long currentSubVersion = subResource.getLong("version");
            final String subResourceId = subResource.getString("id");
            final Long subResourceRegisteredVersion = versionPerResource.get(subResourceId);
            if(subResourceRegisteredVersion == null) {
                versionPerResource.put(subResourceId, currentSubVersion);
                finalSubResources.add(subResource);
            } else if(subResourceRegisteredVersion < currentSubVersion) {
                versionPerResource.put(subResourceId, currentSubVersion);
                for (int j = 0; j < finalSubResources.size(); j++) {
                    if(finalSubResources.getJsonObject(i).getString("id").equals(subResourceId)) {
                        finalSubResources.set(j, subResource);
                    }
                }
            }
        }
        merged.withSubResources(finalSubResources);
        return merged;
    }
}
