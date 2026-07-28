package com.pwb.infra.storage.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("app.storage")
public class StorageProperties {

    private StorageProviderType provider;

    private long maxFileSizeBytes;

    private S3 s3;

    private Local local;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class S3 {

        private String bucket;
        private String region;
        private String accessKey;
        private String secretKey;
        private String endpoint;
        private boolean pathStyleAccess;
        private int presignedUrlExpirationMinutes;
        private long multipartUploadThresholdBytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Local {

        private String basePath;
    }
}