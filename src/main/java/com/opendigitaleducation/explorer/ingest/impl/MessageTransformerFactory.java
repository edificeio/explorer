package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.MessageTransformer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MessageTransformerFactory {
    static Logger log = LoggerFactory.getLogger(MessageTransformerFactory.class);
    public static MessageTransformer create(final JsonObject configuration) {
        final String transformerId =  configuration.getString("id", "");
        final MessageTransformer transformer;
        switch (transformerId) {
            case "htmlTruncate":
                transformer = new HtmlTruncateMessageTransformer();
                break;
            case "htmlAnalyse":
                transformer = new HtmlAnalyserMessageTransformer();
                break;
            case "error":
                transformer = new HtmlAnalyserMessageTransformer();
                break;
            default:
                log.error("Cannot find message transformer " + transformerId);
                transformer = new NoopMessageTransformer();
        }
        transformer.configure(configuration);
        return transformer;
    }
}
