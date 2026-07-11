package com.pwb.backend.shared.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class StorageConfig {

  private final StorageProperties properties;

  @Bean
  public S3Client s3Client() {
    AwsBasicCredentials credentials = AwsBasicCredentials.create(
        properties.getAccessKey(), properties.getSecretKey());

    S3ClientBuilder builder = S3Client.builder()
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .region(Region.of(properties.getRegion()));

    if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
      builder.endpointOverride(URI.create(properties.getEndpoint()))
          .forcePathStyle(true);
    }

    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    AwsBasicCredentials credentials = AwsBasicCredentials.create(
        properties.getAccessKey(), properties.getSecretKey());

    S3Presigner.Builder builder = S3Presigner.builder()
        .credentialsProvider(StaticCredentialsProvider.create(credentials))
        .region(Region.of(properties.getRegion()));

    if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
      builder.endpointOverride(URI.create(properties.getEndpoint()))
          .serviceConfiguration(S3Configuration.builder()
              .pathStyleAccessEnabled(true)
              .build());
    }

    return builder.build();
  }
}
