package com.pwb.backend.modules.liveroom.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.liveroom")
public class LiveRoomProperties {

    @Min(4)
    private int codeLength;

    @NotBlank
    private String alphabet;

    @Min(1)
    private int lockTtlSeconds;

    @Min(1)
    private int codeCollisionMaxRetries;

    @Min(1)
    private int phase1TtlSeconds;

    @Min(60)
    private int phase2TtlSeconds;

    @Min(1)
    private int hostGracePeriodMinutes;

    @Min(1)
    private int emptyRoomGraceMinutes;

    @Min(1)
    private int orphanThresholdMinutes;

    @NotBlank
    private String orphanScanCron;

    @NotBlank
    private String lifecycleCleanupCron;

    private boolean keyspaceListenerEnabled;

    @Min(2)
    private int maxParticipants;

    private int handshakeTimeoutSeconds;

    private int heartbeatIncomingMs;

    private int heartbeatOutgoingMs;

    @Min(60)
    private int waitingEntryTtlSeconds;

    @Min(60)
    private int membersTtlSeconds;

    @Min(1)
    private int waitingEntryMaxAgeSeconds;

    @Min(60)
    private int temporaryTokenTtlSeconds;

    @NotBlank
    private String listenerRole;
}