package com.pwb.backend.shared.outbox.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.outbox")
@Validated
public class OutboxProperties {

    private boolean enabled = true;
    @Min(1)
    private int batchSize = 20;
    @Min(1)
    private int pollIntervalMs = 30000;
    @Min(1)
    private int deadLetterAfterRetries = 5;
    @Min(0)
    private int initialBackoffSeconds = 1;
    @Min(1)
    private int maxBackoffSeconds = 60;
    @NotBlank
    private String encryptionKey;
    @Min(1)
    private int keyVersion = 1;
    /**
     * Optional CSV of {@code version:secret} pairs so the cipher can still
     * decrypt payloads encrypted by an older key while the active key has
     * rotated forward. The active key is added implicitly via
     * {@code encryption-key} + {@code key-version}.
     */
    private String legacyKeys = "";
}