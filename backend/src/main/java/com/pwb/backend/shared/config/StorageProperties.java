package com.pwb.backend.shared.config;

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

    /**
     * Explicit origin allowlist applied to bucket CORS. Driven from the same
     * property the web CORS config reads
     * ({@code app.security.cors.allowed-origins}) so the two cannot drift.
     * Wildcards are intentionally not honoured — bucket CORS combined with a
     * presigned upload URL would let any origin overwrite an object.
     */
    private List<String> corsAllowedOrigins = new ArrayList<>();

    /**
     * Explicit header allowlist applied to bucket CORS. Narrowed to the
     * headers we actually sign and rely on so wildcard-headers is not on by
     * default.
     */
    private List<String> corsAllowedHeaders = new ArrayList<>();
}