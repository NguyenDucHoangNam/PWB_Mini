package com.pwb.infra.storage.dto;

import lombok.Value;

@Value
public class UploadResult {

    String key;
    long sizeBytes;
    String contentType;
    String etag;
}
