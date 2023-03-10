package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MessageTransformer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.entcore.common.explorer.ExplorerMessage.CONTENT_HTML_KEY;
import static org.entcore.common.explorer.ExplorerMessage.CONTENT_KEY;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

public class HtmlAnalyserMessageTransformer implements MessageTransformer {

    public static final int DEFAULT_MIN_LENGTH = 8500;
    /**
     * Minimum length (bound excluded) of contentHtml to be processed by this analyser.
     */
    private int minLength = DEFAULT_MIN_LENGTH;

    @Override
    public void configure(final JsonObject configuration) {
        minLength = configuration.getInteger("minLength", DEFAULT_MIN_LENGTH);
    }

    /**
     * Analyse contentHtml iff it is bigger than {@code minLength}, store the result in the field {@code content}
     * and remove {@code contentHtml}
     * @param messages Messages to trasnform.
     * @return Transformed messags
     */
    @Override
    public List<ExplorerMessageForIngest> transform(final List<ExplorerMessageForIngest> messages) {
        for (final ExplorerMessageForIngest message : messages) {
            final JsonObject messageMessage = message.getMessage();
            final String contentHtml = messageMessage.getString(CONTENT_HTML_KEY);
            if(isNotBlank(contentHtml) && contentHtml.length() > minLength) {
                messageMessage.put(CONTENT_KEY, parseHtml(contentHtml));
                messageMessage.remove(CONTENT_HTML_KEY);
            }
            final JsonArray subResources = message.getMessage().getJsonArray("subresrouces");
            if(subResources != null) {
                for (final Object element : subResources) {
                    final JsonObject subresource = (JsonObject) element;
                    final String subContentHtml = subresource.getString(CONTENT_HTML_KEY);
                    if(isNotBlank(subContentHtml) && subContentHtml.length() > minLength) {
                        subresource.put(CONTENT_HTML_KEY, parseHtml(subContentHtml));
                        subresource.remove(CONTENT_HTML_KEY);
                    }
                }
            }
        }
        return messages;
    }

    public static String parseHtml(final String originalHtmlContent) {
        final Document doc = Jsoup.parse(originalHtmlContent);
        return doc.text();
    }
}
