package com.pwb.backend.modules.audio.service;

import com.pwb.backend.modules.audio.dto.request.ConfirmUploadRequest;
import com.pwb.backend.modules.audio.dto.request.PresignedUrlRequest;
import com.pwb.backend.modules.audio.dto.response.ConfirmUploadResponse;
import com.pwb.backend.modules.audio.dto.response.DemoListItemResponse;
import com.pwb.backend.modules.audio.dto.response.DemoStatusResponse;
import com.pwb.backend.modules.audio.dto.response.PresignedUrlResponse;
import com.pwb.backend.modules.audio.dto.response.RotateKeyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DemoService {

    PresignedUrlResponse generatePresignedUploadUrl(UUID ownerId, PresignedUrlRequest request);

    ConfirmUploadResponse confirmUpload(UUID ownerId, ConfirmUploadRequest request);

    DemoStatusResponse getStatus(UUID ownerId, UUID demoId);

    RotateKeyResponse rotateKey(UUID ownerId, UUID demoId);

    Page<DemoListItemResponse> list(UUID ownerId, Pageable pageable);
}