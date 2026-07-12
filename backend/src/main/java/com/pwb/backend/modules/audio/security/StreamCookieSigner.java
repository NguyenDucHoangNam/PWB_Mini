package com.pwb.backend.modules.audio.security;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.common.security.HttpClientContextResolver;
import com.pwb.backend.modules.audio.config.StreamProperties;
import com.pwb.backend.modules.share.exception.ShareErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamCookieSigner {

    private static final String CLAIM_SHARE_TOKEN = "shareToken";
    private static final String CLAIM_CLIENT_IP_SUBNET = "clientIpSubnet";
    private static final String CLAIM_DEMO_ID = "demoId";

    private final StreamProperties properties;
    private final HttpClientContextResolver clientContextResolver;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] secretBytes = properties.getCookieSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issue(UUID shareToken, String clientIpSubnet, UUID demoId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getCookieTtlSeconds());
        return Jwts.builder()
                .issuer("pwb-stream")
                .claim(CLAIM_SHARE_TOKEN, shareToken.toString())
                .claim(CLAIM_CLIENT_IP_SUBNET, clientIpSubnet)
                .claim(CLAIM_DEMO_ID, demoId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims verify(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("pwb-stream")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(ShareErrorCode.IP_MISMATCH,
                    "Secure session cookie expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ShareErrorCode.IP_MISMATCH,
                    "Invalid secure session cookie: " + ex.getMessage());
        }
    }

    public String resolveClientIpSubnet(HttpServletRequest request) {
        String clientIp = clientContextResolver.resolveIp(request);
        if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    "Cannot resolve client IP for stream cookie");
        }
        int mask = isIpv6(clientIp)
                ? properties.getCidrMaskBitsIpv6()
                : properties.getCidrMaskBitsIpv4();
        return toSubnet(clientIp, mask);
    }

    public boolean ipMatchesSubnet(String clientIp, String subnet) {
        if (clientIp == null || subnet == null) {
            return false;
        }
        int slash = subnet.indexOf('/');
        if (slash < 0) {
            return subnet.equalsIgnoreCase(clientIp);
        }
        String addrPart = subnet.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(subnet.substring(slash + 1));
        } catch (NumberFormatException ex) {
            return false;
        }
        byte[] subnetBytes;
        byte[] candidateBytes;
        try {
            subnetBytes = InetAddress.getByName(addrPart).getAddress();
            candidateBytes = InetAddress.getByName(clientIp).getAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
        return sameFamily(subnetBytes, candidateBytes) && matchCidr(subnetBytes, candidateBytes, prefix);
    }

    private static boolean isIpv6(String ip) {
        return ip.contains(":");
    }

    private static String toSubnet(String ip, int maskBits) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            byte[] raw = addr.getAddress();
            int totalBits = raw.length * 8;
            int safeMask = Math.max(0, Math.min(maskBits, totalBits));
            byte[] masked = applyMask(raw, safeMask);
            return InetAddress.getByAddress(masked).getHostAddress() + "/" + safeMask;
        } catch (UnknownHostException ex) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    "Invalid client IP: " + ip);
        }
    }

    private static byte[] applyMask(byte[] input, int prefixBits) {
        byte[] out = new byte[input.length];
        int fullBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;
        System.arraycopy(input, 0, out, 0, fullBytes);
        if (remainingBits > 0 && fullBytes < out.length) {
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            out[fullBytes] = (byte) (input[fullBytes] & mask);
        }
        return out;
    }

    private static boolean sameFamily(byte[] a, byte[] b) {
        return a.length == b.length;
    }

    private static boolean matchCidr(byte[] network, byte[] candidate, int prefixBits) {
        int totalBits = network.length * 8;
        int safeBits = Math.max(0, Math.min(prefixBits, totalBits));
        int fullBytes = safeBits / 8;
        int remainingBits = safeBits % 8;
        if (!Arrays.equals(Arrays.copyOfRange(network, 0, fullBytes),
                Arrays.copyOfRange(candidate, 0, fullBytes))) {
            return false;
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits) & 0xFF;
        return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
    }
}
