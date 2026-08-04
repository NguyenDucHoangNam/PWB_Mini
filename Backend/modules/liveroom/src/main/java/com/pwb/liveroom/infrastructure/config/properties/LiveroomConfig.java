package com.pwb.liveroom.infrastructure.config.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.liveroom")
public class LiveroomConfig {

    private Room room = new Room();
    private CodeLookup codeLookup = new CodeLookup();
    private Moderation moderation = new Moderation();
    private Chat chat = new Chat();
    private Rtc rtc = new Rtc();

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

    @Getter
    @Setter
    public static class Chat {

        private Duration retention = Duration.ofDays(90);
    }

    @Getter
    @Setter
    public static class Rtc {

        private List<IceServer> iceServers = new ArrayList<>(List.of(
                new IceServer(List.of("stun:stun.l.google.com:19302"), null, null)));

        private int maxSdpLength = 16384;

        private int maxCandidateLength = 1024;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IceServer {

        private List<String> urls = new ArrayList<>();

        private String username;

        private String credential;
    }
}