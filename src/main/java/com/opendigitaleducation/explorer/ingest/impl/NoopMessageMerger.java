package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MergeMessagesResult;
import com.opendigitaleducation.explorer.ingest.MessageMerger;
import org.entcore.common.explorer.ExplorerMessage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Preserve the original list.
 */
public class NoopMessageMerger implements MessageMerger {
    @Override
    public String getId() {
        return "noop";
    }

    @Override
    public MergeMessagesResult mergeMessages(List<ExplorerMessageForIngest> messagesFromReader) {
        final Map<String, List<ExplorerMessageForIngest>> messagesByResourceUniqueId = messagesFromReader.stream()
                .collect(Collectors.groupingBy(ExplorerMessage::getResourceUniqueId));
        return new MergeMessagesResult(
                messagesFromReader,
                messagesByResourceUniqueId);
    }
}
