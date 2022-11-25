 package com.opendigitaleducation.explorer.ingest;

import java.util.List;
import java.util.Map;

public class MergeMessagesResult {
    /** Messages that should be treated by the job.*/
    private final List<ExplorerMessageForIngest> messagesToTreat;
    /** All messages that were merged by their resource unique id.*/
    private final Map<String, List<ExplorerMessageForIngest>> messagesByResourceUniqueId;

    public MergeMessagesResult(List<ExplorerMessageForIngest> messagesToTreat, Map<String, List<ExplorerMessageForIngest>> messagesByResourceUniqueId) {
        this.messagesToTreat = messagesToTreat;
        this.messagesByResourceUniqueId = messagesByResourceUniqueId;
    }

    public List<ExplorerMessageForIngest> getMessagesToTreat() {
        return messagesToTreat;
    }

    public Map<String, List<ExplorerMessageForIngest>> getMessagesByResourceUniqueId() {
        return messagesByResourceUniqueId;
    }

}
