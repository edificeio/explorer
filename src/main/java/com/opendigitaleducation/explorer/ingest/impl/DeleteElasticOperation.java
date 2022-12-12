package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerElasticOperation;
import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;

public class DeleteElasticOperation implements ExplorerElasticOperation {

    private final ExplorerMessageForIngest message;

    private DeleteElasticOperation(final ExplorerMessageForIngest message) {
        this.message = message;
    }

    public static DeleteElasticOperation create(final ExplorerMessageForIngest message) {
        return new DeleteElasticOperation(message);
    }

    @Override
    public ExplorerMessageForIngest getMessage() {
        return message;
    }
}
