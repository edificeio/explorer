package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MergeMessagesResult;
import com.opendigitaleducation.explorer.ingest.MessageMerger;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.ArrayList;
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
        final Map<String, ExplorerMessageForIngest> messagesToTreat = new HashMap<>();
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
        return new MergeMessagesResult(new ArrayList<>(messagesToTreat.values()), messagesByResource);
    }
    private ExplorerMessageForIngest mergeMessageIntoExistingOne(final ExplorerMessageForIngest oldMessage,
                                                                 final ExplorerMessageForIngest newMessage) {
        final ExplorerMessageForIngest merged = new ExplorerMessageForIngest(newMessage);
        final JsonArray existingSubResources = oldMessage.getSubresources();
        if(existingSubResources != null) {
            for (int i = 0; i < existingSubResources.size(); i++) {
                final JsonObject subResource = existingSubResources.getJsonObject(i);
                final String subResourceId = subResource.getString("id");
                if(subResource.getBoolean("deleted", false)) {
                    merged.withSubResource(subResourceId, true);
                } else {
                    final ExplorerMessage.ExplorerContentType type;
                    final String content;
                    if(subResource.containsKey("contentPdf")){
                        type =  ExplorerMessage.ExplorerContentType.Pdf;
                        content = subResource.getString("contentPdf");
                    } else if(subResource.containsKey("contentHtml")){
                        type =  ExplorerMessage.ExplorerContentType.Html;
                        content = subResource.getString("contentHtml");
                    } else {
                        type = ExplorerMessage.ExplorerContentType.Text;
                        content = subResource.getString("content");
                    }
                    merged.withSubResourceContent(subResourceId, content, type);
                }
            }
        }
        return merged;
    }
}
