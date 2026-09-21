package com.pwb.liveroom.application.command;

import java.util.Set;
import java.util.UUID;


public record Actor(UUID userId, String email, Set<String> authorities) {

    public Actor {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }
}