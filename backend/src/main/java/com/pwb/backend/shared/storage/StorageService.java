package com.pwb.backend.shared.storage;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface StorageService {

  void uploadFile(String key, InputStream content, long contentLength, String contentType);

  void deleteFile(String key);

  void deleteFiles(List<String> keys);

  byte[] getFileBytes(String key);

  String generatePresignedUploadUrl(String key, String contentType, long contentLength, int expirationMinutes);

  String generatePresignedDownloadUrl(String key, int expirationMinutes);

  String generatePresignedDownloadUrl(String key, int expirationSeconds, Map<String, String> responseHeaders);

  String getPublicUrl(String key);

  boolean verifyFile(String key, long expectedSize);

  String getFileContentType(String key);

  long getObjectSize(String key);

  byte[] getObjectRange(String key, long start, long end);

  InputStream getObjectStream(String key);

  void copyObject(String sourceKey, String destinationKey);

  void setObjectTags(String key, Map<String, String> tags);
}
