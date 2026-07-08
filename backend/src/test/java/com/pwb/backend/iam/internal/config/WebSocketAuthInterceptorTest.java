package com.pwb.backend.iam.internal.config;

import com.pwb.backend.iam.internal.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

  private JwtService jwtService;
  private WebSocketAuthInterceptor interceptor;
  private final MessageChannel channel = mock(MessageChannel.class);

  @BeforeEach
  void setUp() {
    jwtService = mock(JwtService.class);
    interceptor = new WebSocketAuthInterceptor(jwtService);
    SecurityContextHolder.clearContext();
  }

  @Test
  void preSend_connectWithoutAuthHeader_throws() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    AccessDeniedException ex = assertThrows(AccessDeniedException.class,
        () -> interceptor.preSend(msg, channel));
    assertEquals("Unauthorized WebSocket connection", ex.getMessage());
  }

  @Test
  void preSend_connectWithInvalidToken_throws() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setNativeHeader("Authorization", "Bearer bad.jwt.token");
    Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    when(jwtService.isTokenValid("bad.jwt.token")).thenReturn(false);

    AccessDeniedException ex = assertThrows(AccessDeniedException.class,
        () -> interceptor.preSend(msg, channel));
    assertEquals("Invalid JWT token", ex.getMessage());
  }

  @Test
  void preSend_connectWithValidToken_setsUser() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setNativeHeader("Authorization", "Bearer good.jwt.token");
    accessor.setLeaveMutable(true);
    Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    when(jwtService.isTokenValid("good.jwt.token")).thenReturn(true);
    when(jwtService.extractEmail("good.jwt.token")).thenReturn("test@gmail.com");
    when(jwtService.extractRole("good.jwt.token")).thenReturn("USER");

    Message<?> result = interceptor.preSend(msg, channel);
    StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
    assertNotNull(out.getUser());
    assertEquals("test@gmail.com", out.getUser().getName());
  }

  @Test
  void preSend_subscribeWithoutPriorConnect_throws() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    AccessDeniedException ex = assertThrows(AccessDeniedException.class,
        () -> interceptor.preSend(msg, channel));
    assertEquals("WebSocket session is not authenticated", ex.getMessage());
  }

  @Test
  void preSend_disconnect_returnsMessageUnchanged() {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
    Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    Message<?> result = interceptor.preSend(msg, channel);
    assertNotNull(result);
  }

  @Test
  void preSend_messageWithoutAccessor_passesThrough() {
    Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(
        MessageBuilder.createMessage(new byte[0], new org.springframework.messaging.support.GenericMessage<>(new byte[0]).getHeaders()),
        StompHeaderAccessor.class) == null
        ? new org.springframework.messaging.support.GenericMessage<>(new byte[0]).getHeaders()
        : new org.springframework.messaging.support.GenericMessage<>(new byte[0]).getHeaders());
    Message<?> result = interceptor.preSend(msg, channel);
    assertNotNull(result);
    assertNull(StompHeaderAccessor.wrap(result).getCommand());
  }
}