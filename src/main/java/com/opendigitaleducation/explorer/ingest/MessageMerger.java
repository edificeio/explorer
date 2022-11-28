package com.opendigitaleducation.explorer.ingest;

import java.util.List;

public interface MessageMerger {


    String getId();
    /**
     * Merge messages concerning the same resource.
     * @param messagesFromReader Messages
     * @return
     */
    MergeMessagesResult mergeMessages(final List<ExplorerMessageForIngest> messagesFromReader);
}
