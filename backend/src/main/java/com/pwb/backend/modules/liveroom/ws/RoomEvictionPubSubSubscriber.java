package com.pwb.backend.modules.liveroom.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.dto.ws.RoomEvictionEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RoomEvictionPubSubSubscriber implements MessageListener {

    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final LocalRoomSessionRegistry localRoomSessionRegistry;

    private RedisMessageListenerContainer container;

    @PostConstruct
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this, new ChannelTopic(LiveRoomRedisKeys.ROOM_EVICTION_PUBSUB_CHANNEL));
        container.afterPropertiesSet();
        container.start();
        log.info("ROOM_EVICTION_PUBSUB_LISTENER_STARTED channel={}",
                LiveRoomRedisKeys.ROOM_EVICTION_PUBSUB_CHANNEL);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
            log.info("ROOM_EVICTION_PUBSUB_LISTENER_STOPPED");
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String event = node.path("event").asText(null);
            String roomCode = node.path("roomCode").asText(null);
            if (!RoomEvictionEvent.EVENT_FORCE_CLOSE_ROOM_SESSIONS.equals(event) || roomCode == null) {
                log.debug("ROOM_EVICTION_PUBSUB_IGNORED payload={}", body);
                return;
            }
            int evicted = localRoomSessionRegistry.evictRoom(roomCode);
            log.info("ROOM_EVICTION_PUBSUB_DISPATCHED roomCode={} evictedLocal={}", roomCode, evicted);
        } catch (Exception ex) {
            log.warn("ROOM_EVICTION_PUBSUB_PARSE_FAILED reason={} payload={}", ex.getMessage(), body);
        }
    }
}
