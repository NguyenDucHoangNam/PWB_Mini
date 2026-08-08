package com.pwb.infra.storage.config;

import com.pwb.infra.storage.properties.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URI;

@Slf4j
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@EnableRetry
public class StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        log.info("Initializing S3 client: bucket={}, region={}, endpoint={}",
                s3.getBucket(), s3.getRegion(), s3.getEndpoint());

        var builder = S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(credentialsProvider(s3))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .build());

        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }

        return builder.build();
    }

    /**
     * Backs the transfer manager, and is where parallelism actually comes from.
     *
     * <p>{@code S3TransferManager} on a plain {@code S3AsyncClient} does <em>not</em> split anything: it
     * moves the whole object over one stream. Without {@code multipartEnabled} the transfer manager is
     * only a nicer API over the same single-stream transfer, which is what this was before — including
     * the {@code >25 MB} upload path that its name suggested was already multipart.
     */
    @Bean
    public S3AsyncClient s3AsyncClient(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        log.info("Initializing S3 async client: multipart threshold={}B, part size={}B",
                s3.getMultipartThresholdBytes(), s3.getMultipartPartSizeBytes());

        var builder = S3AsyncClient.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(credentialsProvider(s3))
                .multipartEnabled(true)
                .multipartConfiguration(multipart -> multipart
                        .thresholdInBytes(s3.getMultipartThresholdBytes())
                        .minimumPartSizeInBytes(s3.getMultipartPartSizeBytes()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .build());

        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.getS3();
        log.info("Initializing S3 presigner: region={}, endpoint={}", s3.getRegion(), s3.getEndpoint());

        var builder = S3Presigner.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(credentialsProvider(s3))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .build());

        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3TransferManager s3TransferManager(S3AsyncClient asyncClient) {
        log.info("Initializing S3 Transfer Manager");
        return S3TransferManager.builder().s3Client(asyncClient).build();
    }

    private AwsCredentialsProvider credentialsProvider(StorageProperties.S3 s3) {
        if (s3.getAccessKey() != null && !s3.getAccessKey().isBlank()
                && s3.getSecretKey() != null && !s3.getSecretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
