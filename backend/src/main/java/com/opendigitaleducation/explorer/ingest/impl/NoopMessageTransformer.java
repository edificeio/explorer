package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MessageTransformer;

import java.util.List;

public class NoopMessageTransformer implements MessageTransformer {
    @Override
    public List<ExplorerMessageForIngest> transform(final List<ExplorerMessageForIngest> messages) {
        return messages;
    }
}
