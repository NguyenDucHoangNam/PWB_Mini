package com.pwb.web.security;

import java.util.UUID;

@SuppressWarnings("unused")
public final class CurrentUserSampleController {

    public void authenticatedUserMethod(@CurrentUser AuthenticatedUser user) {
    }

    public void uuidMethod(@CurrentUser UUID userId) {
    }

    public void nonAnnotatedMethod(AuthenticatedUser user) {
    }

    public void stringMethod(@CurrentUser String name) {
    }

    private CurrentUserSampleController() {
    }
}