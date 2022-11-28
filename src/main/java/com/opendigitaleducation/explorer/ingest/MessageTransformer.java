package com.opendigitaleducation.explorer.ingest;

import java.util.List;

public interface MessageTransformer {

    List<ExplorerMessageForIngest> transform(List<ExplorerMessageForIngest> messages);
}
