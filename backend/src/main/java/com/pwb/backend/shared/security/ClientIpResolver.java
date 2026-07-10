package com.pwb.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ClientIpResolver {

    private static final String UNKNOWN = "Unknown";
    private static final String LOOPBACK_IPV4_PREFIX = "127.";
    private static final String LOOPBACK_IPV6_SHORT = "::1";
    private static final String LOOPBACK_IPV6_FULL = "0:0:0:0:0:0:0:1";
    private static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

    private final List<TrustedNetwork> trustedNetworks;

    public ClientIpResolver(
        @Value("${app.security.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}") String trustedProxiesCsv
    ) {
        this.trustedNetworks = parseTrustedProxies(trustedProxiesCsv);
    }

    public String current() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return UNKNOWN;
        }
        return resolve(attributes.getRequest());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String remoteAddr = request.getRemoteAddr();
        if (isBlank(remoteAddr)) {
            return UNKNOWN;
        }

        if (!isTrusted(remoteAddr)) {

            return remoteAddr;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (!isUsableForwardedHeader(forwarded)) {
            return remoteAddr;
        }

        String[] chain = forwarded.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String candidate = chain[i].trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!isTrusted(candidate)) {
                return candidate;
            }
        }

        return remoteAddr;
    }

    private List<TrustedNetwork> parseTrustedProxies(String csv) {
        if (csv == null || csv.isBlank()) {
            return defaultLoopbackNetworks();
        }
        List<TrustedNetwork> result = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            try {
                result.add(TrustedNetwork.parse(entry));
            } catch (UnknownHostException ex) {
                log.warn("Skipping malformed trusted-proxy entry '{}': {}", entry, ex.getMessage());
            }
        }
        if (result.isEmpty()) {
            return defaultLoopbackNetworks();
        }
        return Collections.unmodifiableList(result);
    }

    private List<TrustedNetwork> defaultLoopbackNetworks() {

        List<TrustedNetwork> defaults = new ArrayList<>();
        for (String ip : List.of("127.0.0.1", "::1", LOOPBACK_IPV6_FULL)) {
            try {
                defaults.add(TrustedNetwork.exact(ip));
            } catch (UnknownHostException ex) {
                log.warn("Skipping loopback default '{}': {}", ip, ex.getMessage());
            }
        }
        try {
            defaults.add(TrustedNetwork.cidr("127.0.0.0/8"));
        } catch (UnknownHostException ex) {
            log.warn("Skipping loopback CIDR default: {}", ex.getMessage());
        }
        return defaults;
    }

    private boolean isUsableForwardedHeader(String forwarded) {
        return forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded);
    }

    private boolean isTrusted(String remoteAddr) {
        if (isBlank(remoteAddr)) {
            return false;
        }
        for (TrustedNetwork network : trustedNetworks) {
            if (network.matches(remoteAddr)) {
                return true;
            }
        }

        if (remoteAddr.startsWith(LOOPBACK_IPV4_PREFIX)) {
            return true;
        }

        return LOOPBACK_IPV6_SHORT.equals(remoteAddr) || LOOPBACK_IPV6_FULL.equals(remoteAddr);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class TrustedNetwork {
        private final String address;
        private final Integer prefixLength;
        private final byte[] addrBytes;

        private TrustedNetwork(String address, Integer prefixLength, byte[] addrBytes) {
            this.address = address;
            this.prefixLength = prefixLength;
            this.addrBytes = addrBytes;
        }

        static TrustedNetwork exact(String ip) throws UnknownHostException {
            return new TrustedNetwork(ip, null, InetAddress.getByName(ip).getAddress());
        }

        static TrustedNetwork cidr(String cidr) throws UnknownHostException {
            int slash = cidr.indexOf('/');
            if (slash <= 0 || slash == cidr.length() - 1) {
                throw new UnknownHostException("CIDR requires '/<prefix>' suffix: " + cidr);
            }
            String ipPart = cidr.substring(0, slash);
            int prefix;
            try {
                prefix = Integer.parseInt(cidr.substring(slash + 1));
            } catch (NumberFormatException ex) {
                throw new UnknownHostException("CIDR prefix is not numeric: " + cidr);
            }
            byte[] raw = InetAddress.getByName(ipPart).getAddress();
            int maxBits = raw.length * 8;
            if (prefix < 0 || prefix > maxBits) {
                throw new UnknownHostException("CIDR prefix out of range: " + cidr);
            }
            return new TrustedNetwork(ipPart, prefix, raw);
        }

        static TrustedNetwork parse(String entry) throws UnknownHostException {
            return entry.contains("/") ? cidr(entry) : exact(entry);
        }

        boolean matches(String candidate) {
            try {
                byte[] candBytes = InetAddress.getByName(candidate).getAddress();
                if (candBytes.length != addrBytes.length) {
                    return false;
                }
                if (prefixLength == null) {
                    return Arrays.equals(candBytes, addrBytes);
                }
                int fullBytes = prefixLength / 8;
                int remBits = prefixLength % 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (candBytes[i] != addrBytes[i]) {
                        return false;
                    }
                }
                if (remBits > 0) {
                    int mask = (0xFF00 >> remBits) & 0xFF;
                    if ((candBytes[fullBytes] & mask) != (addrBytes[fullBytes] & mask)) {
                        return false;
                    }
                }
                return true;
            } catch (UnknownHostException ex) {
                return false;
            }
        }

        @Override
        public String toString() {
            return prefixLength == null ? address : address + "/" + prefixLength;
        }
    }
}