package com.pwb.backend.shared.service;

import java.io.InputStream;
import java.util.List;

public interface StorageService {

  void uploadFile(String key, InputStream content, long contentLength, String contentType);

  void deleteFile(String key);

  void deleteFiles(List<String> keys);

  byte[] getFileBytes(String key);

  String generatePresignedUploadUrl(String key, String contentType, long contentLength, int expirationMinutes);

  String generatePresignedDownloadUrl(String key, int expirationMinutes);

  String getPublicUrl(String key);

  boolean verifyFile(String key, long expectedSize);

  String getFileContentType(String key);
}
