package com.pwb.backend.security;

import java.util.UUID;

public interface AuthenticatedUser {

    UUID getId();

    String getUsername();

    String getRole();
}