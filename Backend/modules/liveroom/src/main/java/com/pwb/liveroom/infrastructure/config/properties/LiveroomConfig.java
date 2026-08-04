package com.pwb.liveroom.infrastructure.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.liveroom")
public class LiveroomConfig {

    private Room room = new Room();
    private CodeLookup codeLookup = new CodeLookup();
    private Moderation moderation = new Moderation();

    @Getter
    @Setter
    public static class Room {


        private int defaultCapacity = 7;


        private int codeGenerationAttempts = 5;


        private Duration undoEndWindow = Duration.ofSeconds(5);


        private Duration ownerLeaveDebounce = Duration.ofSeconds(3);


        private Duration emptyTimeout = Duration.ofMinutes(5);


        private Duration idempotencyKeyTtl = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class CodeLookup {

        private int maxAttempts = 5;

        private Duration window = Duration.ofHours(1);
    }

    @Getter
    @Setter
    public static class Moderation {


        private Duration kickCooldown = Duration.ofMinutes(5);


        private Duration micUnmuteCooldown = Duration.ofSeconds(30);
    }
}