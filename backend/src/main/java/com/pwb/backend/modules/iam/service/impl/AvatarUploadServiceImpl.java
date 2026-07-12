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

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private static final int MAX_DIMENSION = 4096;

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_MAGIC = new byte[]{0x47, 0x49, 0x46, 0x38};

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

        byte[] originalBytes;
        try {
            originalBytes = file.getBytes();
        } catch (IOException ex) {
            log.warn("Failed to read avatar bytes for userId={}: {}", userId, ex.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to read avatar bytes: " + ex.getMessage());
        }

        ImageFormat format = detectFormat(originalBytes, file.getContentType());

        String extension = format == ImageFormat.PNG ? "png" : "jpg";
        String storageContentType = format == ImageFormat.PNG ? "image/png" : "image/jpeg";
        byte[] sanitizedBytes = stripAndResize(originalBytes, format);

        String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + extension;

        ObjectStorageService.StoredObject stored;
        try {
            stored = objectStorageService.putObject(
                    StorageBucket.AVATAR,
                    key,
                    sanitizedBytes,
                    storageContentType,
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

    private static ImageFormat detectFormat(byte[] bytes, String declaredContentType) {
        if (matchesMagic(bytes, PNG_MAGIC)) {
            return ImageFormat.PNG;
        }
        if (matchesMagic(bytes, JPEG_MAGIC)) {
            return ImageFormat.JPEG;
        }
        if (matchesMagic(bytes, GIF_MAGIC)) {
            throw new BusinessException(IamErrorCode.AVATAR_INVALID_TYPE,
                    "GIF is not supported; use PNG or JPEG");
        }
        String declared = declaredContentType == null ? "" : declaredContentType.toLowerCase(Locale.ROOT);
        if (!"image/png".equals(declared) && !"image/jpeg".equals(declared) && !"image/jpg".equals(declared)) {
            throw new BusinessException(IamErrorCode.AVATAR_INVALID_TYPE,
                    "Declared content type does not match file signature");
        }
        throw new BusinessException(IamErrorCode.AVATAR_INVALID_TYPE,
                "File signature does not match a supported image format");
    }

    private static boolean matchesMagic(byte[] data, byte[] magic) {
        if (data == null || data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] stripAndResize(byte[] originalBytes, ImageFormat format) {
        BufferedImage source;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(originalBytes)) {
            source = ImageIO.read(bais);
        } catch (IOException ex) {
            throw new BusinessException(IamErrorCode.AVATAR_INVALID_TYPE,
                    "Could not decode image: " + ex.getMessage());
        }
        if (source == null) {
            throw new BusinessException(IamErrorCode.AVATAR_INVALID_TYPE,
                    "Unsupported or corrupted image data");
        }
        if (source.getWidth() > MAX_DIMENSION || source.getHeight() > MAX_DIMENSION) {
            throw new BusinessException(IamErrorCode.AVATAR_INVALID_TYPE,
                    "Image dimensions exceed " + MAX_DIMENSION + "px");
        }

        BufferedImage rendered = new BufferedImage(
                source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rendered.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            String formatName = format == ImageFormat.PNG ? "png" : "jpg";
            if (!ImageIO.write(rendered, formatName, baos)) {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                        "Image writer unavailable for format " + formatName);
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to encode sanitized image: " + ex.getMessage());
        }
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

    private enum ImageFormat { PNG, JPEG }
}
