package com.pwb.web.security;

@SuppressWarnings("unused")
public final class CurrentUserAgentSampleController {

    public void annotatedStringMethod(@CurrentUserAgent String ua) {
    }

    public void nonAnnotatedMethod(String ua) {
    }

    private CurrentUserAgentSampleController() {
    }
}