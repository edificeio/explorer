package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MessageTransformer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

public class HtmlTruncateMessageTransformer implements MessageTransformer {

    private static final int DEFAULT_MAX_LENGTH = 8500;
    private int maxLength = DEFAULT_MAX_LENGTH;

    @Override
    public void configure(final JsonObject configuration) {
        maxLength = configuration.getInteger("maxLength", DEFAULT_MAX_LENGTH);
    }

    @Override
    public List<ExplorerMessageForIngest> transform(final List<ExplorerMessageForIngest> messages) {
        for (final ExplorerMessageForIngest message : messages) {
            final String contentHtml = message.getMessage().getString("contentHtml");
            if(isNotBlank(contentHtml)) {
                message.getMessage().put("contentHtml", truncate(contentHtml));
            }
            final JsonArray subResources = message.getMessage().getJsonArray("subresrouces");
            if(subResources != null) {
                for (final Object element : subResources) {
                    final JsonObject subresource = (JsonObject) element;
                    final String subContentHtml = subresource.getString("contentHtml");
                    if(isNotBlank(subContentHtml)) {
                        subresource.put("contentHtml", truncate(subContentHtml));
                    }
                }
            }
        }
        return messages;
    }

    private String truncate(final String originalHtmlContent) {
        return originalHtmlContent.substring(0, maxLength);
    }
}
