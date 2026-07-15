package com.pwb.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.pwb.backend.repository.elasticsearch")
public class ElasticsearchConfig {

}
