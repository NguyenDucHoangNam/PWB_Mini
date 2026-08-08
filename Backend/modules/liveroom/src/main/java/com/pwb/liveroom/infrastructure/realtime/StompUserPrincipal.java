package com.pwb.liveroom.infrastructure.realtime;

import com.pwb.web.security.AuthenticatedUser;

import java.security.Principal;
import java.util.UUID;


public record StompUserPrincipal(UUID userId, AuthenticatedUser user) implements Principal {

    public static StompUserPrincipal of(AuthenticatedUser user) {
        return new StompUserPrincipal(UUID.fromString(user.getUserId()), user);
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}