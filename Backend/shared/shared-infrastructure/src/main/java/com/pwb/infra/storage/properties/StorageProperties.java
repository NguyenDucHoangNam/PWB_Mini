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

    private long maxFileSizeBytes;

    private S3 s3;

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

        /**
         * Above this, {@code upload(InputStream, …)} streams through the transfer manager instead of a
         * single {@code putObject}. Unrelated to the two settings below, which decide whether a transfer
         * is split into parts at all.
         */
        private long multipartUploadThresholdBytes;

        /**
         * Size above which the S3 client splits a transfer into parts and moves them in parallel, and how
         * big those parts are. This is what makes a transfer to a distant bucket fast: one TCP stream is
         * capped by the bandwidth-delay product no matter how much bandwidth there is, so a 12 MB file
         * over a link to another region spends most of its time waiting rather than moving.
         *
         * <p>5 MiB is S3's minimum part size — anything smaller is rejected for all but the last part.
         * Defaults here rather than in yml so a deployment that never sets them still gets split
         * transfers.
         */
        private long multipartThresholdBytes = 5L * 1024 * 1024;
        private long multipartPartSizeBytes = 5L * 1024 * 1024;
    }
}