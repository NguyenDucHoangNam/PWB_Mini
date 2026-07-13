package com.pwb.backend.modules.liveroom.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBroadcastMessage {

    public static final String EVENT = "CHAT_RECEIVED";

    private String event;

    private ChatPayload data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatPayload {

        private UUID senderId;

        private String senderDisplayName;

        private String type;

        private String content;

        private Instant timestamp;
    }
}
