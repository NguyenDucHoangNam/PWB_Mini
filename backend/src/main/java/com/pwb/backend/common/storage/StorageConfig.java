package com.pwb.backend.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class StorageConfig {

    @Bean
    public ObjectStorageService objectStorageService(ObjectStorageProperties properties) {
        return new S3ObjectStorageService(properties);
    }
}
