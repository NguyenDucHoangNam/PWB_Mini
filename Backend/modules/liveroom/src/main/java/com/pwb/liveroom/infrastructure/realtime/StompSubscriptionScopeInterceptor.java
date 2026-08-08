package com.pwb.liveroom.infrastructure.realtime;

import com.pwb.liveroom.application.support.RoomSubscriptionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class StompSubscriptionScopeInterceptor implements ChannelInterceptor {

    static final String ROOM_TOPIC_PREFIX = "/topic/liveroom/";


    private static final List<String> BROKER_PREFIXES = List.of("/topic/", "/queue/", "/user/");

    private final RoomSubscriptionPolicy subscriptionPolicy;


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            return checkPublish(message, accessor);
        }
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return message;
        }

        if (!(accessor.getUser() instanceof StompUserPrincipal principal)) {
            throw new MessageDeliveryException(message, "WS_UNAUTHENTICATED");
        }

        UUID roomId = parseRoomId(destination);
        if (roomId == null || !subscriptionPolicy.canSubscribe(principal.userId(), roomId)) {
            log.warn("Rejected room subscription: userId={} destination={}",
                    principal.userId(), destination);
            throw new MessageDeliveryException(message, "WS_UNAUTHORIZED");
        }

        log.debug("Room subscription accepted: userId={} destination={}", principal.userId(), destination);
        return message;
    }


    private Message<?> checkPublish(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || BROKER_PREFIXES.stream().noneMatch(destination::startsWith)) {
            return message;
        }
        log.warn("Rejected client publish to broker destination: destination={}", destination);
        throw new MessageDeliveryException(message, "WS_UNAUTHORIZED");
    }


    private UUID parseRoomId(String destination) {
        String remainder = destination.substring(ROOM_TOPIC_PREFIX.length());
        int slash = remainder.indexOf('/');
        String candidate = slash < 0 ? remainder : remainder.substring(0, slash);
        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}