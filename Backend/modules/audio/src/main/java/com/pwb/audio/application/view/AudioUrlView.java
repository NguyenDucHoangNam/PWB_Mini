package com.pwb.audio.application.view;

import java.net.URL;
import java.time.Instant;

public record AudioUrlView(
        URL url,
        Instant expiresAt
) {
}
