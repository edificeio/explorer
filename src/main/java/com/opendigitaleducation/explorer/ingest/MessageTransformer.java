package com.opendigitaleducation.explorer.ingest;

import java.util.List;

/**
 * A mapper of ingestion messages that transforms (or not) a list of messages in another list of messages of the same
 * size (no reduction is allowed).
 */
public interface MessageTransformer {
    /**
     * Maps a list of messages to another list of messages whose order remain intact.
     *
     * @param messages Messages to trasnform.
     * @return The list of mapped messages (the order must be preserved)
     */
    List<ExplorerMessageForIngest> transform(List<ExplorerMessageForIngest> messages);
}
