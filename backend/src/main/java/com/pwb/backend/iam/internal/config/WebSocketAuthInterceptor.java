package com.pwb.backend.iam.internal.config;

import com.pwb.backend.iam.internal.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

  private final JwtService jwtService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    StompCommand command = accessor.getCommand();

    switch (command) {
      case CONNECT -> handleConnect(accessor);
      case SUBSCRIBE, SEND, MESSAGE -> requireAuthenticatedSession(accessor);
      case DISCONNECT, UNSUBSCRIBE -> { /* no-op: session cleanup handled by Spring */ }
      default -> log.debug("WebSocket frame {} allowed for session", command);
    }

    return message;
  }

  private void handleConnect(StompHeaderAccessor accessor) {
    String authHeader = accessor.getFirstNativeHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.warn("WebSocket CONNECT rejected: Missing or invalid Authorization header");
      throw new AccessDeniedException("Unauthorized WebSocket connection");
    }

    String token = authHeader.substring(7);
    if (!jwtService.isTokenValid(token)) {
      log.warn("WebSocket CONNECT rejected: Invalid JWT token");
      throw new AccessDeniedException("Invalid JWT token");
    }

    String email = jwtService.extractEmail(token);
    String role = jwtService.extractRole(token);
    log.info("WebSocket CONNECT authenticated (user redacted)");

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(email, null,
            List.of(new SimpleGrantedAuthority(role)));

    accessor.setUser(authentication);
  }

  private void requireAuthenticatedSession(StompHeaderAccessor accessor) {
    if (accessor.getUser() == null) {
      log.warn("WebSocket {} rejected: no authenticated session", accessor.getCommand());
      throw new AccessDeniedException("WebSocket session is not authenticated");
    }
  }
}