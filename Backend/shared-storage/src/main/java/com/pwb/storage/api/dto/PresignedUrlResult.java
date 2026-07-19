package com.pwb.storage.api.dto;

import lombok.Value;

import java.net.URL;
import java.time.Instant;

@Value
public class PresignedUrlResult {

    URL url;
    Instant expiresAt;
}
