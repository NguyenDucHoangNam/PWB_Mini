package com.pwb.backend.modules.liveroom.ws;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DelegationPubSubSubscriber implements MessageListener {

    private final RedisConnectionFactory connectionFactory;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private RedisMessageListenerContainer container;

    @PostConstruct
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this, new ChannelTopic(LiveRoomRedisKeys.DELEGATION_PUBSUB_CHANNEL));
        container.afterPropertiesSet();
        container.start();
        log.info("DELEGATION_PUBSUB_LISTENER_STARTED channel={}",
                LiveRoomRedisKeys.DELEGATION_PUBSUB_CHANNEL);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
            log.info("DELEGATION_PUBSUB_LISTENER_STOPPED");
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
            JsonNode userIdNode = node.get("userId");
            JsonNode isControllerNode = node.get("isController");
            if (userIdNode == null || isControllerNode == null) {
                log.debug("DELEGATION_PUBSUB_EVENT_IGNORED payload={}", body);
                return;
            }
            String userId = userIdNode.asText();
            boolean isController = isControllerNode.asBoolean();
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/controller-sync",
                    Map.of(
                            "event", "CONTROLLER_SYNC",
                            "userId", userId,
                            "isController", isController));
            log.info("DELEGATION_PUBSUB_EVENT_DISPATCHED userId={} isController={}", userId, isController);
        } catch (Exception ex) {
            log.warn("DELEGATION_PUBSUB_PARSE_FAILED reason={} payload={}", ex.getMessage(), body);
        }
    }
}
