package com.pwb.iam.infrastructure.search;

import com.pwb.infra.search.SearchIndexDefinition;
import com.pwb.infra.search.SearchIndexNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IamSearchIndexDefinitions {

    @Bean
    public SearchIndexDefinition userSearchIndexDefinition() {
        return new SearchIndexDefinition() {
            @Override
            public String indexName() {
                return SearchIndexNames.USERS;
            }

            @Override
            public String mappingResource() {
                return "search/users-mapping.json";
            }
        };
    }
}
