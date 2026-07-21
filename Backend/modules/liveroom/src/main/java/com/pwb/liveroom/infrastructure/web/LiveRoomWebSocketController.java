package com.pwb.liveroom.infrastructure.web;

import com.pwb.backend.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Controller
public class LiveRoomWebSocketController {

    @MessageMapping("/room/{roomCode}/ping")
    @SendTo("/topic/room/{roomCode}/pong")
    public Map<String, Object> handlePing(
            @DestinationVariable("roomCode") String roomCode,
            @AuthenticationPrincipal AuthenticatedUser user,
            Map<String, Object> payload) {

        log.debug("Ping received: roomCode={}, userId={}", roomCode, user == null ? "anonymous" : user.getId());

        return Map.of(
                "type", "PONG",
                "roomCode", roomCode,
                "echo", payload == null ? Map.of() : payload,
                "serverTime", Instant.now().toString()
        );
    }

    @MessageMapping("/room/{roomCode}/echo")
    @SendToUser("/queue/private/echo")
    public Map<String, Object> handleEcho(
            @DestinationVariable("roomCode") String roomCode,
            @AuthenticationPrincipal AuthenticatedUser user,
            Map<String, Object> payload) {

        log.debug("Echo received: roomCode={}, userId={}", roomCode, user == null ? "anonymous" : user.getId());

        return Map.of(
                "type", "ECHO",
                "roomCode", roomCode,
                "payload", payload == null ? Map.of() : payload,
                "serverTime", Instant.now().toString()
        );
    }
}