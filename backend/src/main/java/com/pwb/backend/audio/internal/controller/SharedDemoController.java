package com.pwb.backend.audio.internal.controller;

import com.pwb.backend.audio.internal.api.DownloadResponse;
import com.pwb.backend.audio.internal.api.RequestOtpResponse;
import com.pwb.backend.audio.internal.api.SharedDemoResponse;
import com.pwb.backend.audio.internal.api.VerifyOtpRequest;
import com.pwb.backend.audio.internal.api.WsTokenResponse;
import com.pwb.backend.audio.internal.config.AudioProperties;
import com.pwb.backend.audio.internal.enums.DemoStatus;
import com.pwb.backend.audio.internal.helper.ClientIpSubnetMasker;
import com.pwb.backend.audio.internal.model.Demo;
import com.pwb.backend.audio.internal.model.DemoDistribution;
import com.pwb.backend.audio.internal.repository.DemoDistributionRepository;
import com.pwb.backend.audio.internal.repository.DemoRepository;
import com.pwb.backend.audio.internal.service.AesKeyCacheService;
import com.pwb.backend.audio.internal.service.BruteForceGuardService;
import com.pwb.backend.audio.internal.service.DistributionCacheService;
import com.pwb.backend.audio.internal.service.OtpService;
import com.pwb.backend.audio.internal.service.SharedTokenGuardService;
import com.pwb.backend.audio.internal.service.StreamSecureCookieService;
import com.pwb.backend.audio.internal.service.StreamSessionService;
import com.pwb.backend.audio.internal.service.TrackPlayService;
import com.pwb.backend.iam.internal.domain.model.User;
import com.pwb.backend.iam.internal.infrastructure.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/v1/demos/shared")
@RequiredArgsConstructor
public class SharedDemoController {

  private static final Pattern VALID_TITLE = Pattern.compile("[\\r\\n\\t;\"]");

  private final DemoDistributionRepository distributionRepository;
  private final DemoRepository demoRepository;
  private final UserRepository userRepository;
  private final DistributionCacheService distributionCacheService;
  private final StreamSecureCookieService streamSecureCookieService;
  private final StreamSessionService streamSessionService;
  private final AesKeyCacheService aesKeyCacheService;
  private final OtpService otpService;
  private final TrackPlayService trackPlayService;
  private final BruteForceGuardService bruteForceGuardService;
  private final SharedTokenGuardService sharedTokenGuardService;
  private final ClientIpSubnetMasker clientIpSubnetMasker;
  private final AudioProperties audioProperties;

  @GetMapping("/{shareToken}")
  public ResponseEntity<ApiResponse<SharedDemoResponse>> getSharedDemo(
      @PathVariable("shareToken") UUID shareToken,
      HttpServletRequest request,
      HttpServletResponse response) {

    if (sharedTokenGuardService.isLocked(shareToken.toString())) {
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Share token temporarily locked");
    }
    sharedTokenGuardService.incrementHit(shareToken.toString(), 300);

    DemoDistribution distribution = loadDistribution(shareToken);

    String ip = ip(request);
    Demo demo = loadActiveDemo(distribution.getDemoId());
    User producer = userRepository.findById(demo.getOwnerId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Producer not found"));

    String subnet = clientIpSubnetMasker.mask(ip);
    StreamSecureCookieService.IssuedCookie cookie = streamSecureCookieService.issueStreamCookie(
        shareToken.toString(), demo.getId(), subnet);

    response.addHeader(HttpHeaders.SET_COOKIE, buildCookieHeader(cookie));

    streamSessionService.registerJti(shareToken.toString(), cookie.jti(),
        Duration.ofSeconds(audioProperties.getStreamSecureCookie().getTtlSeconds()));
    distributionCacheService.put(distribution);

    SharedDemoResponse data = new SharedDemoResponse(
        distribution.getThreadId(),
        distribution.getRecipientEmail(),
        producer.getFullName() != null ? producer.getFullName() : producer.getUsername(),
        distribution.isAllowDownload(),
        demo.getTitle(),
        demo.getDuration() == null ? null : demo.getDuration().doubleValue(),
        demo.getWaveformData(),
        "/api/v1/stream/" + shareToken + "/playlist.m3u8",
        cookie.expiresAt());

    return ResponseEntity.ok(ApiResponse.success("Tải luồng chia sẻ thành công", data));
  }

  @GetMapping("/keys/{shareToken}")
  public ResponseEntity<byte[]> getDecryptionKey(
      @PathVariable("shareToken") UUID shareToken,
      @CookieValue(name = "__Host-pwb_stream_sess", required = false) String cookieToken,
      HttpServletRequest request) {

    StreamSecureCookieService.ParsedCookie parsed = streamSecureCookieService.parse(cookieToken);
    if (parsed == null) {
      throw new BusinessException(ErrorCode.STREAM_SESSION_INVALID, "Missing or invalid stream session cookie");
    }
    if (!shareToken.toString().equals(parsed.shareToken())) {
      throw new BusinessException(ErrorCode.STREAM_SESSION_INVALID, "Cookie share token mismatch");
    }
    if (streamSessionService.isJtiRevoked(parsed.jti())) {
      throw new BusinessException(ErrorCode.IP_MISMATCH, "Session jti has been revoked");
    }

    String ip = ip(request);
    String subnet = clientIpSubnetMasker.mask(ip);
    if (!clientIpSubnetMasker.matches(subnet, parsed.clientIpSubnet())) {
      throw new BusinessException(ErrorCode.IP_MISMATCH, "Client IP does not match session subnet");
    }

    if (distributionCacheService.isRevoked(shareToken.toString())) {
      throw new BusinessException(ErrorCode.LINK_REVOKED, "Shared link has been revoked");
    }

    DemoDistribution distribution = loadDistribution(shareToken);
    if (distribution.isRevoked()) {
      throw new BusinessException(ErrorCode.LINK_REVOKED, "Shared link has been revoked");
    }
    Demo demo = loadActiveDemo(distribution.getDemoId());

    int maxKeys = audioProperties.getPlay().getKeysRequestCountMax();
    if (!streamSessionService.incrementKeysRequestCount(shareToken.toString(), maxKeys)) {
      throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Keys request rate exceeded");
    }

    byte[] keyBytes = aesKeyCacheService.getActiveKey(demo.getId());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(keyBytes);
  }

  @PostMapping("/{shareToken}/track-play")
  public ResponseEntity<ApiResponse<Void>> trackPlay(
      @PathVariable("shareToken") UUID shareToken,
      HttpServletRequest request) {

    loadDistribution(shareToken);
    trackPlayService.recordPlay(shareToken.toString(), request);
    return ResponseEntity.ok(ApiResponse.success("Ghi nhận lượt nghe thành công"));
  }

  @PostMapping("/{shareToken}/request-otp")
  public ResponseEntity<ApiResponse<RequestOtpResponse>> requestOtp(
      @PathVariable("shareToken") UUID shareToken) {
    loadDistribution(shareToken);
    otpService.issueCode(shareToken.toString());
    RequestOtpResponse data = new RequestOtpResponse(
        audioProperties.getOtp().getCooldownSeconds(),
        audioProperties.getOtp().getCodeTtlSeconds());
    return ResponseEntity.ok(ApiResponse.success("OTP issued", data));
  }

  @PostMapping("/{shareToken}/verify-otp")
  public ResponseEntity<ApiResponse<Void>> verifyOtp(
      @PathVariable("shareToken") UUID shareToken,
      @RequestBody VerifyOtpRequest body) {
    if (body == null || body.otp() == null || body.otp().isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, "OTP must be provided");
    }
    loadDistribution(shareToken);
    otpService.verify(shareToken.toString(), body.otp().trim());
    return ResponseEntity.ok(ApiResponse.success("OTP verified"));
  }

  @GetMapping("/{shareToken}/ws-token")
  public ResponseEntity<ApiResponse<WsTokenResponse>> getWsToken(
      @PathVariable("shareToken") UUID shareToken,
      @CookieValue(name = "__Host-pwb_stream_sess", required = false) String cookieToken,
      HttpServletRequest request) {

    StreamSecureCookieService.ParsedCookie parsed = streamSecureCookieService.parse(cookieToken);
    if (parsed == null) {
      throw new BusinessException(ErrorCode.STREAM_SESSION_INVALID, "Missing or invalid stream session cookie");
    }
    if (!shareToken.toString().equals(parsed.shareToken())) {
      throw new BusinessException(ErrorCode.STREAM_SESSION_INVALID, "Cookie share token mismatch");
    }
    if (streamSessionService.isJtiRevoked(parsed.jti())) {
      throw new BusinessException(ErrorCode.WS_TOKEN_INVALID, "Session has been revoked");
    }
    String ip = ip(request);
    String subnet = clientIpSubnetMasker.mask(ip);
    if (!clientIpSubnetMasker.matches(subnet, parsed.clientIpSubnet())) {
      throw new BusinessException(ErrorCode.WS_TOKEN_INVALID, "IP subnet mismatch");
    }
    loadDistribution(shareToken);

    StreamSecureCookieService.IssuedCookie wsToken = streamSecureCookieService.issueWsToken(
        shareToken.toString(), parsed.demoId(), subnet);
    WsTokenResponse data = new WsTokenResponse(wsToken.token(), wsToken.expiresAt(),
        audioProperties.getRevoke().getDistributionRevokedQueue());
    return ResponseEntity.ok(ApiResponse.success("WebSocket token issued", data));
  }

  @GetMapping("/{shareToken}/download")
  public ResponseEntity<ApiResponse<DownloadResponse>> download(
      @PathVariable("shareToken") UUID shareToken,
      @CookieValue(name = "__Host-pwb_stream_sess", required = false) String cookieToken,
      HttpServletRequest request) {

    if (audioProperties.getDownload().isRequireSecureCookie()) {
      StreamSecureCookieService.ParsedCookie parsed = streamSecureCookieService.parse(cookieToken);
      if (parsed == null) {
        throw new BusinessException(ErrorCode.STREAM_SESSION_INVALID, "Stream session cookie required for download");
      }
      if (!shareToken.toString().equals(parsed.shareToken())) {
        throw new BusinessException(ErrorCode.STREAM_SESSION_INVALID, "Cookie share token mismatch");
      }
      if (streamSessionService.isJtiRevoked(parsed.jti())) {
        throw new BusinessException(ErrorCode.IP_MISMATCH, "Session jti revoked");
      }
      String ip = ip(request);
      String subnet = clientIpSubnetMasker.mask(ip);
      if (!clientIpSubnetMasker.matches(subnet, parsed.clientIpSubnet())) {
        throw new BusinessException(ErrorCode.IP_MISMATCH, "IP subnet mismatch");
      }
    }

    DemoDistribution distribution = loadDistribution(shareToken);
    if (distribution.isRevoked() || distributionCacheService.isRevoked(shareToken.toString())) {
      throw new BusinessException(ErrorCode.LINK_REVOKED, "Shared link has been revoked");
    }
    if (!distribution.isAllowDownload()) {
      throw new BusinessException(ErrorCode.DOWNLOAD_PROHIBITED, "Producer disabled downloads");
    }
    Demo demo = loadActiveDemo(distribution.getDemoId());

    return ResponseEntity.ok(ApiResponse.success("Sinh liên kết tải xuống thành công",
        new DownloadResponse("/api/v1/internal/demos/" + demo.getId() + "/presigned-download",
            audioProperties.getDownload().getPresignedUrlTtlSeconds())));
  }

  private DemoDistribution loadDistribution(UUID shareToken) {
    return distributionRepository.findByShareToken(shareToken)
        .orElseThrow(() -> {
          bruteForceGuardService.recordFailure("anonymous");
          return new BusinessException(ErrorCode.LINK_NOT_FOUND, "Shared link does not exist");
        });
  }

  private Demo loadActiveDemo(String demoId) {
    Demo demo = demoRepository.findById(demoId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DEMO_NOT_FOUND, "Demo not found"));
    if (demo.getStatus() != DemoStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.DEMO_NOT_ACTIVE, "Demo is not ACTIVE");
    }
    return demo;
  }

  private String ip(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      int comma = xff.indexOf(',');
      return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }
    return request.getRemoteAddr();
  }

  private String buildCookieHeader(StreamSecureCookieService.IssuedCookie cookie) {
    StringBuilder sb = new StringBuilder();
    sb.append(audioProperties.getStreamSecureCookie().getCookieName()).append('=').append(cookie.token()).append("; ");
    sb.append("Path=/; ");
    sb.append("Max-Age=").append(audioProperties.getStreamSecureCookie().getTtlSeconds()).append("; ");
    sb.append("HttpOnly; ");
    if (audioProperties.getStreamSecureCookie().isCookieSecure()) {
      sb.append("Secure; ");
    }
    sb.append("SameSite=").append(audioProperties.getStreamSecureCookie().getCookieSameSite());
    return sb.toString();
  }
}