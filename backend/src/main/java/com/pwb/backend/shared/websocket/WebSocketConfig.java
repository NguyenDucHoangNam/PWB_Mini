package com.pwb.backend.shared.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
// M1: @EnableWebSocketMessageBroker moved to BackendApplication so the
// shared module does not opt the whole context into STOMP on import.
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final List<String> allowedOrigins;

  public WebSocketConfig(
      @Value("${app.security.cors.allowed-origins:http://localhost:3000}") String allowedOriginsCsv) {
    this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    if (allowedOrigins.contains("*")) {
      throw new IllegalStateException(
          "Refusing to start with wildcard CORS origin in WebSocketConfig. "
              + "Configure 'app.security.cors.allowed-origins' with explicit origins.");
    }
    registry.addEndpoint("/ws")
        .setAllowedOrigins(allowedOrigins.toArray(new String[0]))
        .withSockJS();
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] { 10000, 10000 })
        .setTaskScheduler(wsHeartbeatTaskScheduler());
    registry.setUserDestinationPrefix("/user");
  }

  @Bean
  public ThreadPoolTaskScheduler wsHeartbeatTaskScheduler() {
    // M7: bump pool to 4 — a single thread quickly became the bottleneck
    // for STOMP heartbeats once we crossed a few thousand concurrent
    // sessions. Spring's default pool size for this scheduler is 1, which
    // is too small.
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("ws-heartbeat-thread-");
    return scheduler;
  }
}