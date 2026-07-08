package com.pwb.backend.shared.service.impl;

import com.pwb.backend.shared.config.StorageProperties;
import com.pwb.backend.shared.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3StorageServiceTest {

  private S3Client s3Client;
  private S3Presigner s3Presigner;
  private StorageProperties properties;
  private S3StorageService service;

  @BeforeEach
  void setUp() {
    s3Client = mock(S3Client.class);
    s3Presigner = mock(S3Presigner.class);
    properties = new StorageProperties();
    properties.setBucketName("test-bucket");
    properties.setEndpoint("http://localhost:9000");
    properties.setPublicUrlPrefix("");
    properties.setAutoConfigureCors(false);
    service = new S3StorageService(s3Client, s3Presigner, properties);
  }

  @Test
  void uploadFile_callsS3PutObject() {
    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

    service.uploadFile("key-1", new ByteArrayInputStream("data".getBytes()), 4L, "text/plain");

    verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
    assertEquals("test-bucket", captor.getValue().bucket());
    assertEquals("key-1", captor.getValue().key());
    assertEquals("text/plain", captor.getValue().contentType());
  }

  @Test
  void uploadFile_s3Throws_wrapsInStorageException() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(new RuntimeException("boom"));

    StorageException ex = assertThrows(StorageException.class,
        () -> service.uploadFile("k", new ByteArrayInputStream("x".getBytes()), 1L, "text/plain"));
    assertEquals("Storage upload error", ex.getMessage());
  }

  @Test
  void deleteFile_callsS3DeleteObject() {
    service.deleteFile("k");
    ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(captor.capture());
    assertEquals("k", captor.getValue().key());
  }

  @Test
  void getFileBytes_returnsBytes() {
    ResponseBytes<GetObjectResponse> bytes = ResponseBytes.fromByteArray(
        GetObjectResponse.builder().build(), "hello".getBytes(StandardCharsets.UTF_8));
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytes);

    byte[] result = service.getFileBytes("k");
    assertEquals("hello", new String(result, StandardCharsets.UTF_8));
  }

  @Test
  void getFileBytes_s3Throws_wrapsInStorageException() {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(new RuntimeException("not found"));

    StorageException ex = assertThrows(StorageException.class, () -> service.getFileBytes("k"));
    assertEquals("Storage read error", ex.getMessage());
  }

  @Test
  void generatePresignedUploadUrl_returnsPresignedUrl() throws Exception {
    PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
    when(presigned.url()).thenReturn(URI.create("http://localhost:9000/upload?signed=true").toURL());
    when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

    String url = service.generatePresignedUploadUrl("k", "image/png", 100L, 5);
    assertNotNull(url);
    assertTrue(url.contains("signed=true"));
  }

  @Test
  void generatePresignedDownloadUrl_returnsPresignedUrl() throws Exception {
    PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
    when(presigned.url()).thenReturn(URI.create("http://localhost:9000/get?signed=true").toURL());
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

    String url = service.generatePresignedDownloadUrl("k", 5);
    assertNotNull(url);
    assertTrue(url.contains("signed=true"));
  }

  @Test
  void getPublicUrl_usesPublicUrlPrefix() {
    properties.setPublicUrlPrefix("https://cdn.example.com/");
    String url = service.getPublicUrl("k");
    assertEquals("https://cdn.example.com/k", url);
  }

  @Test
  void getPublicUrl_fallsBackToEndpoint() {
    properties.setPublicUrlPrefix("");
    String url = service.getPublicUrl("k");
    assertEquals("http://localhost:9000/test-bucket/k", url);
  }

  @Test
  void getPublicUrl_appendsSlashWhenPrefixMissing() {
    properties.setPublicUrlPrefix("https://cdn.example.com");
    String url = service.getPublicUrl("k");
    assertEquals("https://cdn.example.com/k", url);
  }

  @Test
  void verifyFile_sizeMatches_returnsTrue() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentLength(42L).build());

    assertTrue(service.verifyFile("k", 42L));
  }

  @Test
  void verifyFile_sizeMismatch_returnsFalse() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentLength(10L).build());

    assertFalse(service.verifyFile("k", 42L));
  }

  @Test
  void verifyFile_s3Throws_returnsFalse() {
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(new RuntimeException("err"));
    assertFalse(service.verifyFile("k", 1L));
  }

  @Test
  void getFileContentType_returnsContentType() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentType("image/png").build());

    assertEquals("image/png", service.getFileContentType("k"));
  }

  @Test
  void getFileContentType_s3Throws_returnsNull() {
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(new RuntimeException("err"));
    assertNull(service.getFileContentType("k"));
  }

  @Test
  void deleteFiles_emptyList_noOp() {
    service.deleteFiles(List.of());
    org.mockito.Mockito.verifyNoInteractions(s3Client);
  }

  @Test
  void deleteFiles_callsBatchDelete() {
    service.deleteFiles(List.of("k1", "k2"));
    ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
    verify(s3Client).deleteObjects(captor.capture());
    assertEquals(2, captor.getValue().delete().objects().size());
  }

  @Test
  void deleteFiles_s3Throws_wrapsInStorageException() {
    when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
        .thenThrow(new RuntimeException("boom"));

    StorageException ex = assertThrows(StorageException.class,
        () -> service.deleteFiles(List.of("k1")));
    assertEquals("Storage bulk delete error", ex.getMessage());
  }
}