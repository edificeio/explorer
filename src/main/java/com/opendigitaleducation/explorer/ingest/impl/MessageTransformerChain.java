package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MessageTransformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageTransformerChain implements MessageTransformer {
    private final List<MessageTransformer> transformers;

    public MessageTransformerChain() {
        transformers = new ArrayList<>();
    }

    public MessageTransformerChain withTransformer(final MessageTransformer... transformersToAdd) {
        transformers.addAll(Arrays.asList(transformersToAdd));
        return this;
    }
    public MessageTransformerChain clearChain() {
        this.transformers.clear();
        return this;
    }

    @Override
    public List<ExplorerMessageForIngest> transform(final List<ExplorerMessageForIngest> messages) {
        if(transformers.isEmpty()) {
            // A quick way out
            return messages;
        }
        List<ExplorerMessageForIngest> transformedMessages = messages;
        for (MessageTransformer transformer : transformers) {
            transformedMessages = transformer.transform(transformedMessages);
        }
        return transformedMessages;
    }
}
