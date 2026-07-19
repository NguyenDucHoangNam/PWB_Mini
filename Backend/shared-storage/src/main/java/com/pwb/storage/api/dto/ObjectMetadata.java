package com.pwb.storage.api.dto;

import lombok.Value;

import java.time.Instant;

@Value
public class ObjectMetadata {

    String key;
    long sizeBytes;
    String contentType;
    Instant lastModified;
}
