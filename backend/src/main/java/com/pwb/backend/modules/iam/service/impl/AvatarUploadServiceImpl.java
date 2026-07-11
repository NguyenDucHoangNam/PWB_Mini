package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.common.storage.ObjectStorageService;
import com.pwb.backend.common.storage.StorageBucket;
import com.pwb.backend.modules.iam.dto.response.AvatarUploadResponse;
import com.pwb.backend.modules.iam.exception.IamErrorCode;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.AvatarUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarUploadServiceImpl implements AvatarUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png");

    private final ObjectStorageService objectStorageService;
    private final UserRepository userRepository;

    @Value("${app.iam.avatar.max-size-bytes:2097152}")
    private long maxSizeBytes;

    @Override
    @Transactional
    public AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile file) {
        validateFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            try {
                String oldKey = extractKeyFromUrl(user.getAvatarUrl(), "avatars/");
                if (oldKey != null) {
                    objectStorageService.deleteObject(StorageBucket.AVATAR, oldKey);
                }
            } catch (Exception ex) {
                log.warn("Failed to delete old avatar for userId={}: {}", userId, ex.getMessage());
            }
        }

        String extension = resolveExtension(file.getContentType());
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + extension;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            log.warn("Failed to read avatar bytes for userId={}: {}", userId, ex.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to read avatar bytes: " + ex.getMessage());
        }

        ObjectStorageService.StoredObject stored;
        try {
            stored = objectStorageService.putObject(
                    StorageBucket.AVATAR,
                    key,
                    bytes,
                    file.getContentType(),
                    Map.of("userId", userId.toString(), "uploadedAt", java.time.Instant.now().toString()));
        } catch (Exception ex) {
            log.warn("Failed to upload avatar for userId={}: {}", userId, ex.getMessage());
            throw new BusinessException(IamErrorCode.AVATAR_UPLOAD_FAILED,
                    "Failed to upload avatar: " + ex.getMessage());
        }

        user.setAvatarUrl(stored.publicUrl());
        userRepository.save(user);

        log.info("AVATAR_UPLOADED userId={} key={} bytes={} contentType={}",
                userId, key, stored.sizeBytes(), stored.contentType());

        return new AvatarUploadResponse(stored.publicUrl(), stored.sizeBytes(), stored.contentType());
    }

    @Override
    @Transactional
    public void deleteAvatar(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            return;
        }
        try {
            String key = extractKeyFromUrl(user.getAvatarUrl(), "avatars/");
            if (key != null) {
                objectStorageService.deleteObject(StorageBucket.AVATAR, key);
            }
        } catch (Exception ex) {
            log.warn("Failed to delete avatar object for userId={}: {}", userId, ex.getMessage());
        }
        user.setAvatarUrl(null);
        userRepository.save(user);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(IamErrorCode.AVATAR_FILE_EMPTY);
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(IamErrorCode.AVATAR_FILE_TOO_LARGE,
                    "Avatar exceeds max size of " + maxSizeBytes + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(IamErrorCode.AVATAR_INVALID_TYPE,
                    "Unsupported content type: " + contentType);
        }
    }

    private static String resolveExtension(String contentType) {
        if (contentType == null) {
            return "jpg";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpg", "image/jpeg" -> "jpg";
            default -> "jpg";
        };
    }

    private static String extractKeyFromUrl(String url, String marker) {
        if (url == null || url.isBlank() || marker == null || marker.isEmpty()) {
            return null;
        }
        int idx = url.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        return url.substring(idx);
    }
}
