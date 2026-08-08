package com.pwb.infra.storage.impl;

import com.pwb.infra.storage.dto.UploadResult;
import com.pwb.infra.storage.exception.StorageErrorCode;
import com.pwb.infra.storage.exception.StorageException;
import com.pwb.infra.storage.properties.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The file-based transfers, which is what the merge pipeline moves songs with.
 *
 * <p>The parallelism itself lives in the client configuration rather than here, so what these cover is
 * the part with branches: the transfer manager reports every failure wrapped in a
 * {@link CompletionException}, and a wrapped failure that is not unwrapped surfaces as a generic
 * storage error — a missing object would then look identical to the bucket being unreachable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageService — file transfers")
class S3StorageServiceFileTransferTest {

    @Mock private S3Client s3Client;
    @Mock private S3TransferManager transferManager;
    @Mock private S3Presigner presigner;

    private S3StorageServiceImpl service;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        StorageProperties properties = StorageProperties.builder()
                .maxFileSizeBytes(50L * 1024 * 1024)
                .s3(StorageProperties.S3.builder().bucket("pwb-test").build())
                .build();
        service = new S3StorageServiceImpl(s3Client, transferManager, presigner, properties);
    }

    private void stubDownload(CompletableFuture<CompletedFileDownload> future) {
        FileDownload download = mock(FileDownload.class);
        when(download.completionFuture()).thenReturn(future);
        when(transferManager.downloadFile(any(DownloadFileRequest.class))).thenReturn(download);
    }

    private void stubUpload(CompletableFuture<CompletedFileUpload> future) {
        FileUpload upload = mock(FileUpload.class);
        when(upload.completionFuture()).thenReturn(future);
        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(upload);
    }

    @Nested
    @DisplayName("downloadToFile")
    class DownloadToFile {

        @Test
        @DisplayName("reports the number of bytes that landed on disk")
        void should_return_written_size() throws IOException {
            Path destination = tempDir.resolve("song.mp3");
            stubDownload(CompletableFuture.supplyAsync(() -> {
                try {
                    Files.write(destination, new byte[2048]);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
                return mock(CompletedFileDownload.class);
            }));

            assertThat(service.downloadToFile("songs/song.mp3", destination)).isEqualTo(2048L);
            assertThat(Files.size(destination)).isEqualTo(2048L);
        }

        @Test
        @DisplayName("turns a missing key into NOT_FOUND rather than a generic download failure")
        void should_map_missing_key() {
            stubDownload(CompletableFuture.failedFuture(
                    NoSuchKeyException.builder().message("no such key").build()));

            assertThatThrownBy(() -> service.downloadToFile("songs/gone.mp3", tempDir.resolve("x.mp3")))
                    .isInstanceOf(StorageException.class)
                    .extracting(ex -> ((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND);
        }

        @Test
        @DisplayName("treats a 404 that is not NoSuchKeyException as NOT_FOUND too")
        void should_map_404_status() {
            stubDownload(CompletableFuture.failedFuture(
                    (S3Exception) S3Exception.builder().statusCode(404).message("missing").build()));

            assertThatThrownBy(() -> service.downloadToFile("songs/gone.mp3", tempDir.resolve("x.mp3")))
                    .isInstanceOf(StorageException.class)
                    .extracting(ex -> ((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageErrorCode.STORAGE_OBJECT_NOT_FOUND);
        }

        @Test
        @DisplayName("keeps anything else a download failure")
        void should_map_other_failures() {
            stubDownload(CompletableFuture.failedFuture(
                    (S3Exception) S3Exception.builder().statusCode(500).message("boom").build()));

            assertThatThrownBy(() -> service.downloadToFile("songs/song.mp3", tempDir.resolve("x.mp3")))
                    .isInstanceOf(StorageException.class)
                    .extracting(ex -> ((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageErrorCode.STORAGE_DOWNLOAD_FAILED);
        }

        @Test
        @DisplayName("rejects a key that fails validation before going near the network")
        void should_reject_bad_key() {
            assertThatThrownBy(() -> service.downloadToFile("../../etc/passwd", tempDir.resolve("x")))
                    .isInstanceOf(StorageException.class);
        }
    }

    @Nested
    @DisplayName("uploadFile")
    class UploadFile {

        @Test
        @DisplayName("reads the size off the file rather than trusting a caller-supplied length")
        void should_report_actual_file_size() throws IOException {
            Path source = tempDir.resolve("merged.mp3");
            Files.write(source, new byte[4096]);

            CompletedFileUpload completed = mock(CompletedFileUpload.class);
            when(completed.response()).thenReturn(PutObjectResponse.builder().eTag("\"etag-1\"").build());
            stubUpload(CompletableFuture.completedFuture(completed));

            UploadResult result = service.uploadFile("songs/merged.mp3", source, "audio/mpeg");

            assertThat(result.getKey()).isEqualTo("songs/merged.mp3");
            assertThat(result.getSizeBytes()).isEqualTo(4096L);
            assertThat(result.getContentType()).isEqualTo("audio/mpeg");
            assertThat(result.getEtag()).isEqualTo("\"etag-1\"");
        }

        @Test
        @DisplayName("fails with a storage error when the source is not there")
        void should_fail_on_missing_source() {
            assertThatThrownBy(() ->
                    service.uploadFile("songs/merged.mp3", tempDir.resolve("nope.mp3"), "audio/mpeg"))
                    .isInstanceOf(StorageException.class)
                    .extracting(ex -> ((StorageException) ex).getErrorCode())
                    .isEqualTo(StorageErrorCode.STORAGE_UPLOAD_FAILED);
        }

        @Test
        @DisplayName("unwraps the transfer manager's CompletionException around a failure")
        void should_unwrap_completion_exception() throws IOException {
            Path source = tempDir.resolve("merged.mp3");
            Files.write(source, new byte[16]);
            stubUpload(CompletableFuture.failedFuture(
                    (S3Exception) S3Exception.builder().statusCode(503).message("slow down").build()));

            assertThatThrownBy(() -> service.uploadFile("songs/merged.mp3", source, "audio/mpeg"))
                    .isInstanceOf(StorageException.class)
                    .hasCauseInstanceOf(S3Exception.class);
        }
    }
}
