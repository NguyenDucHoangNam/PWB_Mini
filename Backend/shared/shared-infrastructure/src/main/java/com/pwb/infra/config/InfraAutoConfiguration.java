package com.pwb.infra.config;

import com.pwb.infra.search.SearchConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@EnableConfigurationProperties(SearchConfig.class)
@ComponentScan(basePackages = {
        "com.pwb.infra.mail",
        "com.pwb.infra.redis",
        "com.pwb.infra.storage",
        "com.pwb.infra.kafka",
        "com.pwb.infra.search"
})
public class InfraAutoConfiguration {
}