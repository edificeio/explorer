package com.opendigitaleducation.explorer.ingest.impl;

import com.opendigitaleducation.explorer.ingest.MessageMerger;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository of message mergers.
 * All message mergers are singletons.
 */
public class MessageMergerRepository {
    public static final Map<String, MessageMerger> mergers = new HashMap<>();

    /**
     * @param mergerId Id of the merger we want
     * @return The unique instance of MessageMerger with the supplied id.
     * @throws IllegalArgumentException When the id is unknown
     */
    public static MessageMerger getMerger(final String mergerId) {
        return mergers.computeIfAbsent(mergerId, k -> {
            /*if(StringUtils.isBlank(k) || "default".equalsIgnoreCase(mergerId)) {
                return new DefaultMessageMerger();
            } else if("noop".equalsIgnoreCase(mergerId)) {
                return new NoopMessageMerger();
            } else {
                throw new IllegalArgumentException("merger.type.unknown." + mergerId);
            }*/
            return new NoopMessageMerger();
        });
    }
}
