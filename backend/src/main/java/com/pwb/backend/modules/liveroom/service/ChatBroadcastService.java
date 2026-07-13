package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.config.ChatProperties;
import com.pwb.backend.modules.liveroom.dto.ws.ChatBroadcastMessage;
import com.pwb.backend.modules.liveroom.dto.ws.ChatFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBroadcastService {

    private final ChatProperties properties;
    private final RoomMemberReader roomMemberReader;
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(String roomCode, UUID senderId, ChatFrame frame) {
        if (roomCode == null || roomCode.isBlank()) {
            log.warn("CHAT_DROPPED senderId={} reason=missing_room", senderId);
            return;
        }
        if (senderId == null) {
            log.warn("CHAT_DROPPED roomCode={} reason=missing_sender", roomCode);
            return;
        }
        if (frame == null) {
            log.warn("CHAT_DROPPED roomCode={} senderId={} reason=missing_frame",
                    roomCode, senderId);
            return;
        }
        String type = normalizeType(frame.getType());
        if (type == null) {
            log.warn("CHAT_DROPPED roomCode={} senderId={} reason=invalid_type type={}",
                    roomCode, senderId, frame.getType());
            return;
        }
        String content = frame.getContent();
        if (!isValidContent(type, content)) {
            log.warn("CHAT_DROPPED roomCode={} senderId={} reason=invalid_content type={}",
                    roomCode, senderId, type);
            return;
        }
        if (exceedsPayloadSize(content)) {
            log.warn("CHAT_DROPPED roomCode={} senderId={} reason=payload_too_large type={}",
                    roomCode, senderId, type);
            return;
        }
        if (!roomMemberReader.isMember(roomCode, senderId)) {
            log.warn("CHAT_SENDER_NOT_IN_ROOM roomCode={} senderId={}", roomCode, senderId);
            return;
        }
        String displayName = roomMemberReader.findDisplayName(roomCode, senderId).orElse(null);
        ChatBroadcastMessage message = ChatBroadcastMessage.builder()
                .event(ChatBroadcastMessage.EVENT)
                .data(ChatBroadcastMessage.ChatPayload.builder()
                        .senderId(senderId)
                        .senderDisplayName(displayName)
                        .type(type)
                        .content(content)
                        .timestamp(Instant.now())
                        .build())
                .build();
        try {
            messagingTemplate.convertAndSend(
                    String.format(properties.getChatBroadcastDestination(), roomCode),
                    message);
            if (ChatFrame.TYPE_TEXT.equals(type)) {
                log.info("CHAT_TEXT_SENT roomCode={} senderId={} length={}",
                        roomCode, senderId, content.length());
            } else {
                log.info("CHAT_REACTION_SENT roomCode={} senderId={} emojiCode={}",
                        roomCode, senderId, codePointSummary(content));
            }
        } catch (Exception ex) {
            log.warn("CHAT_BROADCAST_FAILED roomCode={} senderId={} reason={}",
                    roomCode, senderId, ex.getMessage());
        }
    }

    private String normalizeType(String raw) {
        if (raw == null) {
            return null;
        }
        String upper = raw.toUpperCase();
        if (ChatFrame.TYPE_TEXT.equals(upper) || ChatFrame.TYPE_REACTION.equals(upper)) {
            return upper;
        }
        return null;
    }

    private boolean isValidContent(String type, String content) {
        if (content == null) {
            return false;
        }
        if (ChatFrame.TYPE_TEXT.equals(type)) {
            int len = content.length();
            return len >= 1 && len <= properties.getMaxTextLength();
        }
        return properties.getAllowedEmojis().contains(content);
    }

    private boolean exceedsPayloadSize(String content) {
        if (content == null) {
            return false;
        }
        return content.getBytes(StandardCharsets.UTF_8).length > properties.getMaxPayloadBytes();
    }

    private String codePointSummary(String emoji) {
        if (emoji == null || emoji.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        emoji.codePoints().forEach(cp -> sb.append(String.format("U+%04X", cp)));
        return sb.toString();
    }
}
