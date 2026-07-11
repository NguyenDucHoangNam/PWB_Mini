package com.pwb.backend.audio.internal.controller;

import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.model.Demo;
import com.pwb.backend.audio.internal.model.DemoDistribution;
import com.pwb.backend.audio.internal.repository.DemoDistributionRepository;
import com.pwb.backend.audio.internal.repository.DemoRepository;
import com.pwb.backend.audio.internal.service.DownloadAuditService;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/demos")
@RequiredArgsConstructor
public class DownloadController {

  private static final Set<String> ALLOWED_EXT = Set.of("wav", "flac", "mp3", "m4a", "aac");

  private final DemoRepository demoRepository;
  private final DemoDistributionRepository distributionRepository;
  private final StorageService storageService;
  private final DownloadAuditService downloadAuditService;
  private final AudioProperties audioProperties;

  @GetMapping("/{demoId}/presigned-download")
  public ResponseEntity<?> getPresignedDownloadUrl(
      @PathVariable("demoId") String demoId,
      @CookieValue(name = "__Host-pwb_stream_sess", required = false) String cookieToken,
      HttpServletRequest request) {

    Demo demo = demoRepository.findById(demoId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DEMO_NOT_FOUND, "Demo not found"));

    String s3Key = demo.getOriginalS3Key();
    if (s3Key == null || s3Key.isBlank()) {
      throw new BusinessException(ErrorCode.ORIGINAL_FILE_MISSING, "Original S3 key missing");
    }
    String ext = extension(s3Key);
    if (!ALLOWED_EXT.contains(ext)) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT,
          "Extension '" + ext + "' is not in the allowlist");
    }

    long declaredSize = demo.getFileSize();
    long maxBytes = audioProperties.getDownload().getMaxFileBytes();
    long actualSize;
    try {
      actualSize = storageService.getObjectSize(s3Key);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.ORIGINAL_FILE_MISSING,
          "Cannot read object metadata: " + e.getMessage());
    }
    if (actualSize <= 0) {
      throw new BusinessException(ErrorCode.ORIGINAL_FILE_MISSING, "Original file missing on storage");
    }
    if (actualSize > maxBytes) {
      throw new BusinessException(ErrorCode.FILE_SIZE_MISMATCH, "File exceeds maximum allowed size");
    }
    if (declaredSize > 0 && Math.abs(actualSize - declaredSize) > 0) {
      throw new BusinessException(ErrorCode.FILE_SIZE_MISMATCH,
          "Object size has diverged from declared size");
    }

    String safeTitle = sanitizeFilename(demo.getTitle());
    String fallbackName = "demo_track." + ext;
    String encodedName = StandardCharsets.UTF_8.name() + "''"
        + URLEncoder.encode(safeTitle + "." + ext, StandardCharsets.UTF_8).replace("+", "%20");
    String contentDisposition = "attachment; filename=\"" + fallbackName + "\"; filename*=" + encodedName;

    String presignedUrl;
    int ttl = audioProperties.getDownload().getPresignedUrlTtlSeconds();
    try {
      java.util.Map<String, String> headers = new java.util.HashMap<>();
      headers.put("response-content-disposition", contentDisposition);
      presignedUrl = storageService.generatePresignedDownloadUrl(s3Key, ttl, headers);
    } catch (Exception e) {
      log.error("S3_PRESIGN_FAILED s3Key={} error={}", s3Key, e.getMessage());
      throw new BusinessException(ErrorCode.S3_PRESIGN_FAILED, "Failed to generate presigned URL");
    }

    String distributionId = null;
    if (cookieToken != null) {
      try {
        UUID token = parseShareTokenFromCookie(cookieToken);
        DemoDistribution distribution = distributionRepository.findByShareToken(token).orElse(null);
        if (distribution != null) {
          distributionId = distribution.getId();
        }
      } catch (Exception ignore) {
      }
    }
    if (distributionId != null) {
      String sessionHash = computeSessionHash(request, cookieToken);
      downloadAuditService.record(distributionId, demoId, sessionHash, ip(request), s3Key, actualSize);
    }

    log.info("S3_DOWNLOAD_URL_GENERATED s3Key={} ttlSeconds={}", s3Key, ttl);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, audioProperties.getDownload().getCacheControl())
        .header("X-Content-Type-Options", audioProperties.getDownload().getXContentTypeOptions())
        .contentType(MediaType.APPLICATION_JSON)
        .body(java.util.Map.of(
            "success", true,
            "message", "Sinh liên kết tải xuống thành công",
            "data", java.util.Map.of("downloadUrl", presignedUrl),
            "timestamp", Instant.now().toString()));
  }

  private String extension(String s3Key) {
    int slash = s3Key.lastIndexOf('/');
    String name = slash >= 0 ? s3Key.substring(slash + 1) : s3Key;
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return "";
    }
    return name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
  }

  private String sanitizeFilename(String title) {
    if (title == null) {
      return "demo_track";
    }
    String stripped = title.replaceAll("[\\r\\n\\t;\"']", "");
    stripped = stripped.trim();
    int max = audioProperties.getDownload().getFilenameFallbackMaxLength();
    if (stripped.length() > max) {
      stripped = stripped.substring(0, max);
    }
    if (stripped.isBlank()) {
      return "demo_track";
    }
    return stripped;
  }

  private String ip(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      int comma = xff.indexOf(',');
      return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }
    return request.getRemoteAddr();
  }

  private String computeSessionHash(HttpServletRequest request, String cookieToken) {
    String ua = request.getHeader("User-Agent");
    String acceptLang = request.getHeader("Accept-Language");
    return (ip(request) + "|" + (ua == null ? "" : ua) + "|" + (acceptLang == null ? "" : acceptLang)
        + "|" + (cookieToken == null ? "" : cookieToken)).hashCode() + "";
  }

  private UUID parseShareTokenFromCookie(String cookieToken) {
    String[] parts = cookieToken.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid JWT shape");
    }
    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    int idx = payload.indexOf("\"st\":\"");
    if (idx < 0) {
      throw new IllegalArgumentException("share token claim not found");
    }
    int end = payload.indexOf("\"", idx + 6);
    return UUID.fromString(payload.substring(idx + 6, end));
  }
}