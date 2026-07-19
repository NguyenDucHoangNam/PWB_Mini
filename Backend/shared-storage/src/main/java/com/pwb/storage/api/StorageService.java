package com.pwb.storage.api;

import com.pwb.storage.api.dto.ObjectMetadata;
import com.pwb.storage.api.dto.PresignedUrlResult;
import com.pwb.storage.api.dto.UploadResult;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

public interface StorageService {

    UploadResult upload(String key, InputStream content, long sizeBytes, String contentType);

    UploadResult upload(String key, byte[] content, String contentType);

    InputStream download(String key);

    void delete(String key);

    void deleteAll(List<String> keys);

    boolean exists(String key);

    ObjectMetadata getMetadata(String key);

    PresignedUrlResult generatePresignedUrl(String key, Duration expiration);

    PresignedUrlResult generatePresignedUploadUrl(String key, String contentType, Duration expiration);
}
