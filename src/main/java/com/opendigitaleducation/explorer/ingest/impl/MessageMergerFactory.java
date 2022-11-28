package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.MessageMerger;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class MessageMergerFactory {
    public static final Map<String, MessageMerger> mergers = new HashMap<>();

    public static MessageMerger getMerger(final String mergerType) {
        return mergers.computeIfAbsent(mergerType, k -> {
            if("default".equalsIgnoreCase(mergerType)) {
                return new DefaultMessageMerger();
            } else if(StringUtils.isBlank(k) || "noop".equalsIgnoreCase(mergerType)) {
                return new NoopMessageMerger();
            } else {
                throw new IllegalArgumentException("merger.type.unknown." + mergerType);
            }
        });
    }
}
