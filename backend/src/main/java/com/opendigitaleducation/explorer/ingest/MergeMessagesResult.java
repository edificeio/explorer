 package com.opendigitaleducation.explorer.ingest;

import java.util.List;
import java.util.Map;

 /**
  * Holds a list of ingestion messages that should be treated (the order is not relevant) and a map of messages
  * that should be acknowledged by the reader when one treated message has been treated successfully. The key of the
  * map is the resource unique id of the resource that was concerned by the successful message.
  */
 public class MergeMessagesResult {
    /** Messages that should be treated by the job.*/
    private final List<ExplorerMessageForIngest> messagesToTreat;
    /** All messages that were merged by their resource unique id.*/
    private final Map<String, List<ExplorerMessageForIngest>> messagesToAckByTratedMessageIdQueue;

    public MergeMessagesResult(List<ExplorerMessageForIngest> messagesToTreat, Map<String, List<ExplorerMessageForIngest>> messagesToAckByTratedMessageIdQueue) {
        this.messagesToTreat = messagesToTreat;
        this.messagesToAckByTratedMessageIdQueue = messagesToAckByTratedMessageIdQueue;
    }

    public List<ExplorerMessageForIngest> getMessagesToTreat() {
        return messagesToTreat;
    }

    public Map<String, List<ExplorerMessageForIngest>> getMessagesToAckByTratedMessageIdQueue() {
        return messagesToAckByTratedMessageIdQueue;
    }

}
