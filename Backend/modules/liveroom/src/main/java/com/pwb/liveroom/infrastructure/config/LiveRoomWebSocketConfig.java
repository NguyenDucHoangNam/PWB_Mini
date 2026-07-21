package com.pwb.liveroom.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class LiveRoomWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ENDPOINT = "/ws/liveroom";
    private static final String APP_DESTINATION_PREFIX = "/app";
    private static final String TOPIC_PREFIX = "/topic";
    private static final String QUEUE_PREFIX = "/queue";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(TOPIC_PREFIX, QUEUE_PREFIX);
        registry.setApplicationDestinationPrefixes(APP_DESTINATION_PREFIX);
    }
}