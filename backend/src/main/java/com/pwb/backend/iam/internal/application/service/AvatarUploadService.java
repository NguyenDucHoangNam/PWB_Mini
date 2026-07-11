package com.pwb.backend.iam.internal.application.service;

import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AvatarUploadService {

  private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
  private static final int MAX_DIMENSION_PX = 512;

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

    byte[] resizedBytes;
    int resizedWidth;
    int resizedHeight;
    try {
      BufferedImage original = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
      if (original == null) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
            "Avatar file is not a valid image");
      }
      String formatName = ".png".equals(extension) ? "png" : "jpg";
      BufferedImage resized = downscale(original, MAX_DIMENSION_PX, formatName);
      resizedWidth = resized.getWidth();
      resizedHeight = resized.getHeight();
      resizedBytes = encode(resized, formatName);
    } catch (IOException e) {
      log.error("Failed to process avatar for userId={}", userId, e);
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to process avatar");
    }

    try (ByteArrayInputStream uploadStream = new ByteArrayInputStream(resizedBytes)) {
      storageService.uploadFile(key, uploadStream, resizedBytes.length, contentType);
    } catch (IOException e) {
      log.error("Failed to upload avatar for userId={}", userId, e);
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
          "Failed to upload avatar");
    }
    log.info("Avatar uploaded for userId={} {}x{} ({} bytes)", userId, resizedWidth, resizedHeight, resizedBytes.length);
    return storageService.getPublicUrl(key);
  }

  private static BufferedImage downscale(BufferedImage source, int maxDimension, String formatName) {
    int w = source.getWidth();
    int h = source.getHeight();
    if (w <= maxDimension && h <= maxDimension) {
      return source;
    }
    double scale = Math.min((double) maxDimension / w, (double) maxDimension / h);
    int targetW = Math.max(1, (int) Math.round(w * scale));
    int targetH = Math.max(1, (int) Math.round(h * scale));
    boolean preserveAlpha = "png".equals(formatName);
    int imageType = preserveAlpha
        ? BufferedImage.TYPE_INT_ARGB
        : BufferedImage.TYPE_INT_RGB;
    BufferedImage resized = new BufferedImage(targetW, targetH, imageType);
    Graphics2D g = resized.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(source, 0, 0, targetW, targetH, null);
    } finally {
      g.dispose();
    }
    return resized;
  }

  private static byte[] encode(BufferedImage image, String formatName) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
    if (!writers.hasNext()) {
      throw new IOException("No ImageWriter registered for format " + formatName);
    }
    ImageWriter writer = writers.next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    if (param.canWriteCompressed() && "jpg".equals(formatName)) {
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.85f);
    }
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }
}
