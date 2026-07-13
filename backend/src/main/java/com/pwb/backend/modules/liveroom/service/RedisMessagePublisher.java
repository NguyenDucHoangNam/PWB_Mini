package com.pwb.backend.modules.liveroom.service;

import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessagePublisher {

    private final StringRedisTemplate stringRedisTemplate;

    public void publishDelegationEvent(String payloadJson) {
        try {
            RedisConnectionFactory factory = stringRedisTemplate.getRequiredConnectionFactory();
            try (var connection = factory.getConnection()) {
                long receivers = connection.publish(
                        LiveRoomRedisKeys.DELEGATION_PUBSUB_CHANNEL.getBytes(StandardCharsets.UTF_8),
                        payloadJson.getBytes(StandardCharsets.UTF_8));
                log.debug("REDIS_PUBSUB_PUBLISHED channel={} receivers={}",
                        LiveRoomRedisKeys.DELEGATION_PUBSUB_CHANNEL, receivers);
            }
        } catch (Exception ex) {
            log.warn("REDIS_PUBSUB_PUBLISH_FAILED channel={} reason={}",
                    LiveRoomRedisKeys.DELEGATION_PUBSUB_CHANNEL, ex.getMessage());
        }
    }
}