package com.pwb.backend.iam.internal.service;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Handles avatar upload validation, persistence to object storage, and URL
 * resolution. Keeping this logic out of AuthService ensures the controller
 * only deals with HTTP concerns.
 */
@Slf4j
@Service
public class AvatarUploadService {

  private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

  private final StorageService storageService;
  private final String avatarPrefix;

  public AvatarUploadService(StorageService storageService,
      @Value("${app.storage.avatar-prefix:avatars/}") String avatarPrefix) {
    this.storageService = storageService;
    this.avatarPrefix = avatarPrefix;
  }

  public String uploadAvatar(String userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Avatar file is required");
    }
    if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Avatar image size must not exceed 2MB");
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED,
          "Only accepts .jpg, .jpeg, .png image formats");
    }

    String extension = switch (contentType.toLowerCase()) {
      case "image/png" -> ".png";
      case "image/jpg", "image/jpeg" -> ".jpg";
      default -> "";
    };
    String key = avatarPrefix + userId + "/" + UUID.randomUUID() + extension;

    try {
      storageService.uploadFile(key, file.getInputStream(), file.getSize(), contentType);
    } catch (IOException e) {
      log.error("Failed to upload avatar for userId={}", userId, e);
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to upload avatar");
    }
    return storageService.getPublicUrl(key);
  }
}