package com.pwb.audio.infrastructure.search;

import com.pwb.infra.search.SearchIndexDefinition;
import com.pwb.infra.search.SearchIndexNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioSearchIndexDefinitions {

    @Bean
    public SearchIndexDefinition songSearchIndexDefinition() {
        return new SearchIndexDefinition() {
            @Override
            public String indexName() {
                return SearchIndexNames.SONGS;
            }

            @Override
            public String mappingResource() {
                return "search/songs-mapping.json";
            }
        };
    }

    @Bean
    public SearchIndexDefinition voiceTagSearchIndexDefinition() {
        return new SearchIndexDefinition() {
            @Override
            public String indexName() {
                return SearchIndexNames.VOICE_TAGS;
            }

            @Override
            public String mappingResource() {
                return "search/voice-tags-mapping.json";
            }
        };
    }
}
