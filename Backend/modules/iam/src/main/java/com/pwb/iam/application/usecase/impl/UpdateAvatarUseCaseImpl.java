package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.command.AvatarUpload;
import com.pwb.iam.application.command.UpdateAvatarCommand;
import com.pwb.iam.application.service.AvatarUrlResolver;
import com.pwb.iam.application.usecase.UpdateAvatarUseCase;
import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.model.AvatarPolicy;
import com.pwb.infra.storage.StorageService;
import com.pwb.infra.storage.exception.StorageException;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateAvatarUseCaseImpl implements UpdateAvatarUseCase {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AvatarPolicy avatarPolicy;

    /**
     * Deliberately not annotated {@code @Transactional}: the S3 round trip in the middle can take
     * seconds, and holding a pooled database connection across it is what exhausts the pool under
     * concurrent uploads. Each repository call below opens its own short transaction instead
     * ({@code UserRepositoryImpl} is transactional at the class level).
     */
    @Override
    public String execute(UpdateAvatarCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
        validate(command.file());

        String previousKey = user.getAvatarUrl();
        String key = buildKey(command.userId(), command.file().contentType());

        upload(key, command.file(), command.userId());

        user.changeAvatarUrl(key);
        userRepository.save(user);

        deleteQuietly(previousKey);

        log.info("Avatar uploaded: userId={} key={}", command.userId(), key);
        return avatarUrlResolver.resolve(key);
    }

    private void upload(String key, AvatarUpload file, UUID userId) {
        try (InputStream content = file.openStream()) {
            // Streamed rather than read into a byte[]: a 5 MB heap allocation per concurrent
            // upload is avoidable, and the storage API accepts a stream directly.
            storageService.upload(key, content, file.size(), file.contentType());
        } catch (StorageException | IOException ex) {
            log.error("Failed to upload avatar: userId={}", userId, ex);
            throw new BusinessException(IamErrorCode.SERVICE_UNAVAILABLE, ex);
        }
    }

    /**
     * The new avatar is already live at this point, so failing to remove the previous object is
     * a storage-cleanup concern, not a reason to fail the user's request.
     */
    private void deleteQuietly(String previousKey) {
        if (previousKey == null || previousKey.isBlank()
                || previousKey.startsWith("http://") || previousKey.startsWith("https://")) {
            return;
        }
        try {
            storageService.delete(previousKey);
        } catch (StorageException ex) {
            log.warn("Orphaned previous avatar object: key={} reason={}", previousKey, ex.getMessage());
        }
    }

    private void validate(AvatarUpload file) {
        String contentType = file.contentType() == null
                ? null
                : file.contentType().toLowerCase(Locale.ROOT);

        if (contentType == null || !avatarPolicy.allows(contentType)) {
            throw new BusinessException(IamErrorCode.PROFILE_INVALID_AVATAR_FORMAT);
        }
        if (file.size() > avatarPolicy.maxSizeBytes()) {
            throw new BusinessException(IamErrorCode.PROFILE_AVATAR_TOO_LARGE);
        }
        // Content-Type is supplied by the client and trivially forged; the magic bytes decide
        // whether this is really the image format it claims to be.
        if (!file.hasMagicBytesFor(contentType)) {
            throw new BusinessException(IamErrorCode.PROFILE_INVALID_AVATAR_FORMAT);
        }
    }

    private String buildKey(UUID userId, String contentType) {
        return "avatars/%s/%s%s".formatted(userId, UUID.randomUUID(), extensionFor(contentType));
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
