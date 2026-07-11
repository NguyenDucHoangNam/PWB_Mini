package com.pwb.backend.shared.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.storage")
@Validated
public class StorageProperties {

    @NotBlank
    private String endpoint;
    @NotBlank
    private String bucketName;
    @NotBlank
    private String accessKey;
    @NotBlank
    private String secretKey;
    @NotBlank
    private String region = "us-east-1";
    private String publicUrlPrefix;
    private boolean autoCreateBucket = false;
    private boolean autoConfigureCors = false;

    private List<String> corsAllowedOrigins = new ArrayList<>();

    private List<String> corsAllowedHeaders = new ArrayList<>();
}
