package com.pwb.infra.storage.impl;

import com.pwb.infra.storage.dto.PresignedUrlResult;
import com.pwb.infra.storage.properties.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Drives a real {@link S3Presigner} rather than a mock, because what is being asserted is the shape of
 * the URL the SDK produces and a mock cannot tell us that.
 *
 * <p>These are the guarantees the upload flow is built on. A presigned {@code PUT} only constrains what
 * it signs: whatever is left out is chosen freely by whoever holds the URL. Before this, neither the
 * content type nor the length was signed, so the holder of an upload URL could store a file of any size
 * labelled as anything — {@code text/html} included, which storage then served as a page. If a future
 * change drops either header from the signature that hole reopens silently, with nothing failing and
 * nothing in the logs, which is exactly what these assertions exist to catch.
 */
@DisplayName("S3StorageServiceImpl – what the presigned URLs actually commit to")
class S3StorageServicePresignTest {

    private static final String SIGNED_HEADERS_PARAM = "X-Amz-SignedHeaders=";

    private final S3StorageServiceImpl service = new S3StorageServiceImpl(
            mock(S3Client.class),
            mock(S3TransferManager.class),
            realPresigner(),
            StorageProperties.builder()
                    .s3(StorageProperties.S3.builder().bucket("pwb-test").build())
                    .build());

    private static S3Presigner realPresigner() {
        return S3Presigner.builder()
                .region(Region.of("ap-southeast-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIAEXAMPLEEXAMPLE", "s".repeat(40))))
                .build();
    }

    private static String signedHeaders(PresignedUrlResult result) {
        String query = URLDecoder.decode(result.getUrl().getQuery(), StandardCharsets.UTF_8);
        return java.util.Arrays.stream(query.split("&"))
                .filter(param -> param.startsWith(SIGNED_HEADERS_PARAM))
                .map(param -> param.substring(SIGNED_HEADERS_PARAM.length()))
                .findFirst()
                .orElse("");
    }

    @Nested
    @DisplayName("an upload URL accepts only the file it was issued for")
    class UploadUrl {

        @Test
        @DisplayName("signs both the declared size and the declared content type")
        void signs_content_length_and_content_type() {
            PresignedUrlResult result = service.generatePresignedUploadUrl(
                    "audio/originals/u/track.mp3", "audio/mpeg", 4_096L, Duration.ofHours(1));

            // Storage recomputes the signature over these, so a PUT carrying a different size or a
            // different type is refused before any of its body is accepted.
            assertThat(signedHeaders(result)).contains("content-length").contains("content-type");
        }

        @Test
        @DisplayName("leaves a header unsigned only when the caller declined to pin it")
        void omits_what_was_not_supplied() {
            PresignedUrlResult result = service.generatePresignedUploadUrl(
                    "audio/originals/u/track.mp3", null, 0L, Duration.ofHours(1));

            assertThat(signedHeaders(result)).doesNotContain("content-length").doesNotContain("content-type");
        }
    }

    @Nested
    @DisplayName("a download URL never renders in the browser")
    class DownloadUrl {

        /**
         * An object's stored content type is decided by whoever uploaded it, so a download URL that let
         * the browser act on it would execute an uploaded page on the bucket's own origin. Forcing the
         * disposition is what makes that impossible regardless of what is stored.
         */
        @Test
        @DisplayName("forces Content-Disposition: attachment")
        void forces_attachment_disposition() {
            PresignedUrlResult result =
                    service.generatePresignedUrl("audio/originals/u/track.mp3", Duration.ofMinutes(15));

            String query = URLDecoder.decode(result.getUrl().getQuery(), StandardCharsets.UTF_8);
            assertThat(query).contains("response-content-disposition=attachment");
        }
    }
}
