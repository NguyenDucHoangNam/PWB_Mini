package com.pwb.backend.modules.iam.service;

import com.pwb.backend.modules.iam.dto.response.AvatarUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AvatarUploadService {

    AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile file);

    void deleteAvatar(UUID userId);
}
