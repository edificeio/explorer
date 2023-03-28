package com.opendigitaleducation.explorer.ingest;

import java.util.List;

/**
 * <p>
 * A processor that tries to compress a list of ingestion messages as much as possible to optimize performance and
 * prevent version collision.
 *</p>
 * <p>
 * It should accept a chronologically ordered list of ingestion messages and produce a merge result.
 * </p>
 */
public interface MessageMerger {
    /**
     * @return The unique identifier of this merger (used by the factory).
     */
    String getId();
    /**
     * Merge messages concerning the same resource.
     * @param messagesFromReader Messages
     * @return
     */
    MergeMessagesResult mergeMessages(final List<ExplorerMessageForIngest> messagesFromReader);
}
