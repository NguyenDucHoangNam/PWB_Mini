package com.pwb.liveroom.infrastructure.realtime;

import com.pwb.web.security.AccessTokenAuthenticator;
import com.pwb.web.security.AuthenticatedUser;
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

import java.util.Optional;


@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final AccessTokenAuthenticator accessTokenAuthenticator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        AuthenticatedUser user = Optional
                .ofNullable(accessor.getFirstNativeHeader("Authorization"))
                .flatMap(AccessTokenAuthenticator::extractBearerToken)
                .flatMap(accessTokenAuthenticator::authenticate)
                .orElseThrow(() -> {
                    log.debug("Rejected STOMP CONNECT without a usable access token");
                    return new MessageDeliveryException(message, "WS_UNAUTHENTICATED");
                });

        accessor.setUser(StompUserPrincipal.of(user));
        log.debug("STOMP session authenticated: userId={}", user.getUserId());
        return message;
    }
}