package com.pwb.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucketName = "pwb-media";

    private String region = "ap-southeast-1";

    private String publicUrlPrefix;
}
