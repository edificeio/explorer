package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.ExplorerMessageForIngest;
import com.opendigitaleducation.explorer.ingest.MessageTransformer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

public class HtmlAnalyserMessageTransformer implements MessageTransformer {

    @Override
    public List<ExplorerMessageForIngest> transform(final List<ExplorerMessageForIngest> messages) {
        for (final ExplorerMessageForIngest message : messages) {
            final String contentHtml = message.getMessage().getString("contentHtml");
            if(isNotBlank(contentHtml)) {
                message.getMessage().put("contentHtml", parseHtml(contentHtml));
            }
            final JsonArray subResources = message.getMessage().getJsonArray("subresrouces");
            if(subResources != null) {
                for (final Object element : subResources) {
                    final JsonObject subresource = (JsonObject) element;
                    final String subContentHtml = subresource.getString("contentHtml");
                    if(isNotBlank(subContentHtml)) {
                        subresource.put("contentHtml", parseHtml(subContentHtml));
                    }
                }
            }
        }
        return messages;
    }

    private String parseHtml(final String originalHtmlContent) {
        final Document doc = Jsoup.parse(originalHtmlContent);
        return doc.text();
    }
}
