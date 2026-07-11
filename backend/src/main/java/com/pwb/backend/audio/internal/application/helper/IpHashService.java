package com.pwb.backend.audio.internal.application.helper;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpHashService {

  private final AudioProperties audioProperties;
  private final ClientIpSubnetMasker clientIpSubnetMasker;

  @PostConstruct
  void validateSalt() {
    String salt = audioProperties.getIpHash().getSalt();
    if (salt == null || salt.isBlank()) {
      log.warn("IpHashService salt is blank — IP hashing will fall back to a static dev salt");
    }
  }

  public String hashIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return null;
    }
    return digest("ip:" + ip);
  }

  public String hashSubnetV4(String ip) {
    if (ip == null || ip.isBlank()) {
      return null;
    }
    String subnet = clientIpSubnetMasker.mask(ip);
    return digest("subnet:" + subnet);
  }

  public String hashSession(String ip, String userAgent, String acceptLanguage, String cookieJti) {
    StringBuilder sb = new StringBuilder();
    if (ip != null) sb.append(ip);
    sb.append('|');
    if (userAgent != null) sb.append(userAgent);
    sb.append('|');
    if (acceptLanguage != null) sb.append(acceptLanguage);
    sb.append('|');
    if (cookieJti != null) sb.append(cookieJti);
    return digest("session:" + sb);
  }

  public String hash(String prefix, String value) {
    if (value == null) {
      return null;
    }
    return digest(prefix + ":" + value);
  }

  private String digest(String input) {
    String salt = audioProperties.getIpHash().getSalt();
    if (salt == null || salt.isBlank()) {
      salt = "dev-ip-hash-salt";
    }
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(salt.getBytes(StandardCharsets.UTF_8));
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}