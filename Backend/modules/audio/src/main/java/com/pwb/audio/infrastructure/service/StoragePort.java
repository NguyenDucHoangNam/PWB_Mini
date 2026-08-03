package com.pwb.audio.infrastructure.service;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;

public interface StoragePort {

    URL getPresignedUrl(String s3Key, long expirationSeconds);

    URL getPresignedUploadUrl(String s3Key, long expirationSeconds);

    void delete(String s3Key);

    InputStream download(String s3Key);

    String uploadFromPath(String s3Key, Path sourcePath, long contentLength);

    String uploadBytes(String s3Key, byte[] content, String contentType);
}