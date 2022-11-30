package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MessageTransformer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Transform messages based on a set of rules to produce errors at different stage of the ingestion.
 */
public class ErrorMessageTransformer implements MessageTransformer {

    static Logger log = LoggerFactory.getLogger(ErrorMessageTransformer.class);
    private final List<IngestJobErrorRule> errorRules;

    public ErrorMessageTransformer(List<IngestJobErrorRule> errorRules) {
        this.errorRules = errorRules;
    }

    @Override
    public List<ExplorerMessageForIngest> transform(List<ExplorerMessageForIngest> messages) {
        return messages.stream().map(message -> messageMatchesError(message).map(errorRule ->
                transormMessageToGenerateError(message, errorRule)
        ).orElse(message)).collect(Collectors.toList());
    }

    private Optional<IngestJobErrorRule> messageMatchesError(ExplorerMessageForIngest message) {
        return this.errorRules.stream().filter(errorRule -> {
            if(errorRule.getValuesToTarget() != null) {
                final JsonObject messageBody = message.getMessage();
                final boolean bodyMatch = errorRule.getValuesToTarget().entrySet().stream().allMatch(fieldNameAndValue ->
                        messageBody.getString(fieldNameAndValue.getKey(), "").matches(fieldNameAndValue.getValue())
                );
                if(bodyMatch) {
                    log.debug("Evicting message " + messageBody + " based on " + errorRule);
                } else {
                    return false;
                }
            }
            if(errorRule.getAction() != null && !message.getAction().matches(errorRule.getAction())) {
                return false;
            }
            return errorRule.getPriority() == null || message.getPriority().name().matches(errorRule.getPriority());
        }).findFirst();
    }

    /**
     * Generates a message that will generate an error by setting values with wrong types.
     *
     * @param message   Message that should generate an error
     * @param errorRule
     * @return A transformed version of the message that will generate an error upon ingestion
     */
    private ExplorerMessageForIngest transormMessageToGenerateError(ExplorerMessageForIngest message, IngestJobErrorRule errorRule) {
        final JsonObject duplicate = message.getMessage().copy();
        final ExplorerMessageForIngest ingest = new ExplorerMessageForIngest(
                message.getAction(),
                message.getIdQueue().orElse(null),
                message.getId(),
                duplicate);
        final String pof = errorRule.getPointOfFailure();
        if("es".equalsIgnoreCase(pof)) {
            // raise an error because we specified "public" as being a boolean in the mapping
            ingest.getMessage().put("public", 4);
        } else if("pg-ingest".equalsIgnoreCase(pof)) {
            // raise an error in Postgre because application max length is 100
            ingest.getMessage().put("application", "DebGwKIkgDVcnDbiIDZyVPzfZT8FCwn3ywMBskJdNqJYtVEfNUJEAljXsIfrTTOPLwlOa3Lw5UjX7evnOBfafKTsSLU0ZOfJIvFHB8BqKBjzwTtCNmIrWHk11dfI730KOHuRDYwRSbthCwHNFvfza6KhGexpKBd1uMyWiglZobg31FWFpPszRjhNcZlZRLNGyJprsKjlojkCqu5QxvImSwOhA7DuYQwmHx4zAQevpNi8qgEGKUk4qeoZWMubt5RDrLOiWoAxCEyt3kiNf1Fl2sl4iKBFcaLGUVbJeNVXs7oST5nvTcrh7eXpKPb6yIFWwkawHuxZ8gJ1MBfM7PpboGy3evdLDEqvgf7PUzTwmAMMfsjyn0s2bgzHUi2x2qwokraEhXDYLuqC69MOZESuPSBM5griE6hhKDIogLZAo0ZeujMKny8dgyvGMbMReJgJKQryp6AtJcHP7m8Yf6LVziTuzk0dHXl0J4TSNlWST1IfGVFdAUNgaQ5md7e3RVp0");
        }
        return ingest;
    }
    public static class IngestJobErrorRule {
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

        private final String pointOfFailure;

        public IngestJobErrorRule(String action, String priority, Map<String, String> valuesToTarget, final String pointOfFailure) {
            this.action = action;
            this.priority = priority;
            this.valuesToTarget = valuesToTarget;
            this.pointOfFailure = pointOfFailure;
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

        public String getPointOfFailure() {
            return pointOfFailure;
        }

        @Override
        public String toString() {
            return "IngestJobErrorRule{" +
                    "action='" + action + '\'' +
                    ", priority='" + priority + '\'' +
                    ", valuesToTarget=" + valuesToTarget +
                    ", pointOfFailure='" + pointOfFailure + '\'' +
                    '}';
        }
    }
    public static class IngestJobErrorRuleBuilder {
        private String action;
        private String priority;
        private Map<String, String> valuesToTarget;
        private String pointOfFailure;

        public IngestJobErrorRuleBuilder setPointOfFailure(final String pof) {
            this.pointOfFailure = pof;
            return this;
        }
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
            return new IngestJobErrorRule(action, priority, valuesToTarget, pointOfFailure);
        }
    }
}
