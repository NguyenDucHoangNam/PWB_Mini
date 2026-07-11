package com.pwb.backend.audio.internal.application.helper;

import com.pwb.backend.audio.internal.interfaces.config.AudioProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientIpSubnetMasker {

  private final AudioProperties audioProperties;

  public String mask(String ip) {
    if (ip == null || ip.isBlank()) {
      return "";
    }
    String trimmed = ip.trim();
    if (trimmed.contains(":")) {
      return maskV6(trimmed);
    }
    return maskV4(trimmed);
  }

  private String maskV4(String ip) {
    String[] octets = ip.split("\\.");
    if (octets.length != 4) {
      return ip;
    }
    int mask = audioProperties.getStreamSecureCookie().getCidrMaskBitsV4();
    if (mask >= 32) {
      return ip;
    }
    int kept = Math.max(1, mask / 8);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      if (i < kept) {
        sb.append(octets[i]);
      } else {
        sb.append('0');
      }
      if (i < 3) sb.append('.');
    }
    return sb.toString();
  }

  private String maskV6(String ip) {
    int mask = audioProperties.getStreamSecureCookie().getCidrMaskBitsV6();
    if (mask >= 128) {
      return ip;
    }
    int keptBytes = Math.max(1, mask / 8);
    int colons = 0;
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < ip.length(); i++) {
      char ch = ip.charAt(i);
      if (ch == ':') {
        colons++;
        if (colons > keptBytes / 2 + 1) {
          break;
        }
      }
      out.append(ch);
    }
    while (colons < 7) {
      out.append(":0");
      colons++;
    }
    return out.toString();
  }

  public boolean matches(String subnet1, String subnet2) {
    if (subnet1 == null || subnet2 == null) {
      return false;
    }
    return subnet1.equals(subnet2);
  }
}