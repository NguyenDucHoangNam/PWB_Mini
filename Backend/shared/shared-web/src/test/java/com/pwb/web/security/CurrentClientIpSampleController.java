package com.pwb.web.security;

import java.util.UUID;

@SuppressWarnings("unused")
public final class CurrentClientIpSampleController {

    public void annotatedStringMethod(@CurrentClientIp String ip) {
    }

    public void annotatedUuidMethod(@CurrentClientIp UUID ip) {
    }

    public void nonAnnotatedMethod(String ip) {
    }

    private CurrentClientIpSampleController() {
    }
}