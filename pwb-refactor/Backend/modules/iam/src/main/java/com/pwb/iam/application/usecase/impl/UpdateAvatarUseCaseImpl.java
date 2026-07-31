package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.UpdateAvatarCommand;
import com.pwb.iam.application.usecase.UpdateAvatarUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.dto.UploadResult;
import com.pwb.infra.storage.exception.StorageException;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateAvatarUseCaseImpl implements UpdateAvatarUseCase {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public String execute(UpdateAvatarCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));

        validateFile(command.file());

        String key = generateS3Key(command.userId(), command.file().getContentType());

        try {
            UploadResult result = storageService.upload(
                    key,
                    command.file().getBytes(),
                    command.file().getContentType()
            );

            String avatarUrl = storageService.generatePresignedUrl(key, PRESIGNED_URL_EXPIRATION).getUrl().toString();

            user.changeAvatarUrl(avatarUrl);
            userRepository.save(user);

            log.info("Avatar uploaded: userId={}, key={}", user.getUserId(), key);
            return avatarUrl;

        } catch (StorageException e) {
            log.error("Failed to upload avatar: userId={}", command.userId(), e);
            throw new BusinessException(IamErrorCode.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("Unexpected error during avatar upload: userId={}", command.userId(), e);
            throw new BusinessException(IamErrorCode.SERVICE_UNAVAILABLE, e);
        }
    }

    private void validateFile(org.springframework.web.multipart.MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(IamErrorCode.PROFILE_INVALID_AVATAR_FORMAT);
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(IamErrorCode.PROFILE_AVATAR_TOO_LARGE);
        }
    }

    private String generateS3Key(UUID userId, String contentType) {
        String extension = getExtensionFromContentType(contentType);
        return String.format("avatars/%s/%s%s", userId, UUID.randomUUID(), extension);
    }

    private String getExtensionFromContentType(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
