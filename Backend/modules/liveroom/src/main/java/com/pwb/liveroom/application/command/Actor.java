package com.pwb.liveroom.application.command;

import java.util.Set;
import java.util.UUID;


public record Actor(UUID userId, String email, Set<String> authorities) {

    private static final String ROLE_PRO = "ROLE_PRO";

    public Actor {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }

    public boolean isPro() {
        return authorities.contains(ROLE_PRO);
    }
}