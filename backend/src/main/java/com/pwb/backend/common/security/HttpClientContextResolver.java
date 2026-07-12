package com.pwb.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class HttpClientContextResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN_IP = "unknown";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String UNKNOWN_DEVICE = "Unknown";

    private final TrustedProxyProperties trustedProxyProperties;

    public String resolveIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedProxyProperties.isTrustForwardedHeaders()
                && remote != null
                && isInTrustedCidr(remote)) {
            String forwarded = request.getHeader(X_FORWARDED_FOR_HEADER);
            if (forwarded != null && !forwarded.isBlank()) {
                int commaIndex = forwarded.indexOf(',');
                String first = commaIndex >= 0 ? forwarded.substring(0, commaIndex) : forwarded;
                return first.trim();
            }
        }
        return remote != null && !remote.isBlank() ? remote : UNKNOWN_IP;
    }

    public String resolveUserAgent(HttpServletRequest request) {
        String agent = request.getHeader(USER_AGENT_HEADER);
        return agent == null || agent.isBlank() ? UNKNOWN_DEVICE : agent;
    }

    private boolean isInTrustedCidr(String remote) {
        InetAddress remoteAddr;
        try {
            remoteAddr = InetAddress.getByName(remote);
        } catch (UnknownHostException ex) {
            return false;
        }
        byte[] raw = remoteAddr.getAddress();
        for (String entry : trustedProxyProperties.getCidrs()) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            CidrRange range = CidrRange.parse(entry.trim());
            if (range.matches(raw)) {
                return true;
            }
        }
        return false;
    }

    private record CidrRange(byte[] network, int prefixBits) {

        static CidrRange parse(String cidr) {
            int slash = cidr.indexOf('/');
            String addrPart;
            int prefix;
            if (slash >= 0) {
                addrPart = cidr.substring(0, slash);
                prefix = Integer.parseInt(cidr.substring(slash + 1));
            } else {
                addrPart = cidr;
                prefix = cidr.contains(":") ? 128 : 32;
            }
            try {
                byte[] raw = InetAddress.getByName(addrPart).getAddress();
                return new CidrRange(raw, prefix);
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr, ex);
            }
        }

        boolean matches(byte[] candidate) {
            int prefixBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            if (prefixBytes > 0 && !Arrays.equals(Arrays.copyOfRange(network, 0, prefixBytes),
                    Arrays.copyOfRange(candidate, 0, prefixBytes))) {
                return false;
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            return (network[prefixBytes] & mask) == (candidate[prefixBytes] & mask);
        }
    }
}
