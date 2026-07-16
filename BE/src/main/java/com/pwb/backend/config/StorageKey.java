package com.pwb.backend.config;

import com.pwb.backend.enums.MediaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class StorageKey {

    private final MediaType mediaType;
    private final String fileName;
    private final String fullPath;
    private final long fileSize;
}
