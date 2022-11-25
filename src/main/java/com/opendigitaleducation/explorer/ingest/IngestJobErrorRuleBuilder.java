package com.opendigitaleducation.explorer.ingest;

import java.util.HashMap;
import java.util.Map;

public class IngestJobErrorRuleBuilder {
    private String action;
    private String priority;
    private Map<String, String> valuesToTarget;

    public IngestJobErrorRuleBuilder setAction(String action) {
        this.action = action;
        return this;
    }

    public IngestJobErrorRuleBuilder setPriority(String priority) {
        this.priority = priority;
        return this;
    }

    public IngestJobErrorRuleBuilder setValuesToTarget(Map<String, String> valuesToTarget) {
        this.valuesToTarget = valuesToTarget;
        return this;
    }

    public IngestJobErrorRuleBuilder withValueToTarget(final String fieldName, final String fieldRegexpValue) {
        if(this.valuesToTarget == null) {
            this.valuesToTarget = new HashMap<>();
        }
        this.valuesToTarget.put(fieldName, fieldRegexpValue);
        return this;
    }

    public IngestJobErrorRule createIngestJobErrorRule() {
        return new IngestJobErrorRule(action, priority, valuesToTarget);
    }
}