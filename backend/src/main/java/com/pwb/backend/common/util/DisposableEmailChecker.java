package com.pwb.backend.common.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class DisposableEmailChecker {

    private static final Logger log = LoggerFactory.getLogger(DisposableEmailChecker.class);
    private static final String RESOURCE_PATH = "disposable-email-domains.txt";
    private static final String COMMENT_PREFIX = "#";

    private Set<String> blockedDomains = Collections.emptySet();

    @PostConstruct
    void load() {
        Set<String> domains = new HashSet<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Disposable email blocklist not found on classpath: " + RESOURCE_PATH);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX)) {
                    continue;
                }
                domains.add(trimmed.toLowerCase(Locale.ROOT));
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to load disposable email domain list from " + RESOURCE_PATH, ex);
        }
        if (domains.isEmpty()) {
            throw new IllegalStateException(
                    "Disposable email blocklist is empty: " + RESOURCE_PATH);
        }
        this.blockedDomains = Collections.unmodifiableSet(domains);
        log.info("Loaded {} disposable email domains", blockedDomains.size());
    }

    public boolean isDisposable(String email) {
        if (email == null || email.isBlank() || blockedDomains.isEmpty()) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return blockedDomains.contains(domain);
    }
}