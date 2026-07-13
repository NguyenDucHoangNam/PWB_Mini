package com.pwb.backend.modules.liveroom.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRateLimitWarning {

    public static final String EVENT = "CHAT_RATE_LIMIT_WARNING";

    private String event;

    private String type;

    private int limitPerMinute;

    private Instant timestamp;
}
