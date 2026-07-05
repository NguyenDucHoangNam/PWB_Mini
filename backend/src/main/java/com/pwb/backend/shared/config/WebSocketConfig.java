package com.pwb.backend.shared.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*")
        .withSockJS();
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
    heartbeatScheduler.setPoolSize(1);
    heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-thread-");
    heartbeatScheduler.initialize();

    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[]{10000, 10000})
        .setTaskScheduler(heartbeatScheduler);
    registry.setUserDestinationPrefix("/user");
  }
}
