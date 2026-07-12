package com.pwb.backend.modules.audio.service;

import com.pwb.backend.modules.audio.dto.request.ConfirmUploadRequest;
import com.pwb.backend.modules.audio.dto.request.PresignedUrlRequest;
import com.pwb.backend.modules.audio.dto.response.ConfirmUploadResponse;
import com.pwb.backend.modules.audio.dto.response.DemoStatusResponse;
import com.pwb.backend.modules.audio.dto.response.PresignedUrlResponse;

import java.util.UUID;

public interface DemoService {

    PresignedUrlResponse generatePresignedUploadUrl(UUID ownerId, PresignedUrlRequest request);

    ConfirmUploadResponse confirmUpload(UUID ownerId, ConfirmUploadRequest request);

    DemoStatusResponse getStatus(UUID ownerId, UUID demoId);
}