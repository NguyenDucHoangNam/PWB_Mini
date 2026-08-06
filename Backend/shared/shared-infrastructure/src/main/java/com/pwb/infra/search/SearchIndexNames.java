package com.pwb.infra.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a module's logical index name into the physical one. Every read and write goes through here so a
 * prefix change cannot leave half the application pointing at the old indices.
 */
@Component
@RequiredArgsConstructor
public class SearchIndexNames {

    public static final String SONGS = "songs";
    public static final String VOICE_TAGS = "voice_tags";
    public static final String ROOMS = "rooms";
    public static final String USERS = "users";

    private final SearchConfig config;

    public String physical(String logicalName) {
        String prefix = config.getIndexPrefix();
        if (prefix == null || prefix.isBlank()) {
            return logicalName;
        }
        return prefix + "_" + logicalName;
    }
}
