package com.pwb.backend.modules.liveroom.ws;

import com.pwb.backend.modules.liveroom.constant.LiveRoomRedisKeys;
import com.pwb.backend.modules.liveroom.service.RoomLifecycleService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.liveroom.keyspace-listener-enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedisKeyspaceExpiredListener implements MessageListener {

    private static final String KEYSPACE_EVENT_PATTERN = "__keyevent@*__:expired";

    private final RedisConnectionFactory connectionFactory;
    private final RoomLifecycleService roomLifecycleService;
    private final StringRedisTemplate stringRedisTemplate;

    private RedisMessageListenerContainer container;

    @PostConstruct
    public void start() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this, new PatternTopic(KEYSPACE_EVENT_PATTERN));
        container.start();
        log.info("REDIS_KEYSPACE_LISTENER_STARTED pattern={}", KEYSPACE_EVENT_PATTERN);
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
            log.info("REDIS_KEYSPACE_LISTENER_STOPPED");
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody());
        if (expiredKey == null || !expiredKey.startsWith(LiveRoomRedisKeys.ROOM_STATUS_KEY_PREFIX)) {
            return;
        }
        String roomCode = expiredKey.substring(LiveRoomRedisKeys.ROOM_STATUS_KEY_PREFIX.length());
        if (roomCode.isBlank()) {
            return;
        }
        try {
            roomLifecycleService.closeRoom(roomCode);
            try {
                stringRedisTemplate.delete(LiveRoomRedisKeys.roomDelegatedKey(roomCode));
            } catch (Exception ex) {
                log.warn("ROOM_AUTO_CLOSE_DELEGATED_KEY_DELETE_FAILED roomCode={} reason={}",
                        roomCode, ex.getMessage());
            }
            log.info("ROOM_AUTO_CLOSED_KEYSPACE roomCode={} reason=ttl_expired", roomCode);
        } catch (Exception ex) {
            log.warn("ROOM_AUTO_CLOSE_FAILED roomCode={} reason={}", roomCode, ex.getMessage());
        }
    }
}