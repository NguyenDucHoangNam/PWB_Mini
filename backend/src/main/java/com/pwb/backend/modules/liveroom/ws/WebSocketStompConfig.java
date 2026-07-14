package com.pwb.backend.modules.liveroom.ws;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.pwb.backend.modules.liveroom.config.LiveRoomProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    private final LiveRoomProperties properties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final SignallingRateLimitInterceptor signallingRateLimitInterceptor;
    private final ChatRateLimitInterceptor chatRateLimitInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{properties.getHeartbeatIncomingMs(), properties.getHeartbeatOutgoingMs()})
                .setTaskScheduler(new ThreadPoolTaskScheduler() {{
                    setPoolSize(1);
                    setThreadNamePrefix("liveroom-ws-heartbeat-");
                    initialize();
                }});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                stompAuthChannelInterceptor,
                signallingRateLimitInterceptor,
                chatRateLimitInterceptor);
    }
}
