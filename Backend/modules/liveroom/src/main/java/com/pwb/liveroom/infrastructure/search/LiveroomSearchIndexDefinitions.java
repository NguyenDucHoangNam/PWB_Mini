package com.pwb.liveroom.infrastructure.search;

import com.pwb.infra.search.SearchIndexDefinition;
import com.pwb.infra.search.SearchIndexNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveroomSearchIndexDefinitions {

    @Bean
    public SearchIndexDefinition roomSearchIndexDefinition() {
        return new SearchIndexDefinition() {
            @Override
            public String indexName() {
                return SearchIndexNames.ROOMS;
            }

            @Override
            public String mappingResource() {
                return "search/rooms-mapping.json";
            }
        };
    }
}