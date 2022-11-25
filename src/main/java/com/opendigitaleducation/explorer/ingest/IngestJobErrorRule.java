package com.opendigitaleducation.explorer.ingest;

import java.util.Map;

/**
 * Rule to be used in the context of tests (unit or integration) to force a message to alter the processing of a message.
 */
public class IngestJobErrorRule {
    /**
     * Action of the targeted messages.
     */
    private final String action;
    /**
     * Priority of the targeted messages.
     */
    private final String priority;
    /**
     * Values of messages' fields to target.
     */
    private final Map<String, String> valuesToTarget;

    public IngestJobErrorRule(String action, String priority, Map<String, String> valuesToTarget) {
        this.action = action;
        this.priority = priority;
        this.valuesToTarget = valuesToTarget;
    }

    public String getAction() {
        return action;
    }

    public String getPriority() {
        return priority;
    }

    public Map<String, String> getValuesToTarget() {
        return valuesToTarget;
    }

    @Override
    public String toString() {
        return "IngestJobErrorRule{" +
                "action='" + action + '\'' +
                ", priority='" + priority + '\'' +
                ", valuesToTarget=" + valuesToTarget +
                '}';
    }
}
