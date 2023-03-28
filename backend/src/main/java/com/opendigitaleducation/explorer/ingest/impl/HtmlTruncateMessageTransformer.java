package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MessageTransformer;
import static com.opendigitaleducation.explorer.ingest.impl.HtmlAnalyserMessageTransformer.DEFAULT_MIN_LENGTH;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.entcore.common.explorer.ExplorerMessage.CONTENT_HTML_KEY;

import java.util.List;

public class HtmlTruncateMessageTransformer implements MessageTransformer {

    /**
     * Maximum length (bound included) of contentHtml to be processed by this transformer.
     */
    private int maxLength = DEFAULT_MIN_LENGTH;

    @Override
    public void configure(final JsonObject configuration) {
        maxLength = configuration.getInteger("maxLength", DEFAULT_MIN_LENGTH);
    }

    @Override
    public List<ExplorerMessageForIngest> transform(final List<ExplorerMessageForIngest> messages) {
        for (final ExplorerMessageForIngest message : messages) {
            final String contentHtml = message.getMessage().getString(CONTENT_HTML_KEY);
            if(isNotBlank(contentHtml) && contentHtml.length() <= maxLength) {
                message.getMessage().put(CONTENT_HTML_KEY, truncate(contentHtml));
            }
            final JsonArray subResources = message.getMessage().getJsonArray("subresrouces");
            if(subResources != null) {
                for (final Object element : subResources) {
                    final JsonObject subresource = (JsonObject) element;
                    final String subContentHtml = subresource.getString(CONTENT_HTML_KEY);
                    if(isNotBlank(subContentHtml) && subContentHtml.length() <= maxLength) {
                        subresource.put(CONTENT_HTML_KEY, truncate(subContentHtml));
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
