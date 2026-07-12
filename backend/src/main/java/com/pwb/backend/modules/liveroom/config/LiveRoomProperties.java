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
    private int codeLength = 6;

    @NotBlank
    private String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Min(1)
    private int lockTtlSeconds = 10;

    @Min(1)
    private int codeCollisionMaxRetries = 3;

    @Min(1)
    private int phase1TtlSeconds = 30;

    @Min(60)
    private int phase2TtlSeconds = 14400;

    @Min(1)
    private int hostGracePeriodMinutes = 5;

    @Min(1)
    private int orphanThresholdMinutes = 1;

    @NotBlank
    private String orphanScanCron = "0 */2 * * * *";

    private boolean keyspaceListenerEnabled = false;

    @Min(2)
    private int maxParticipants = 7;

    private int handshakeTimeoutSeconds = 10;

    private int heartbeatIncomingMs = 10000;

    private int heartbeatOutgoingMs = 10000;
}