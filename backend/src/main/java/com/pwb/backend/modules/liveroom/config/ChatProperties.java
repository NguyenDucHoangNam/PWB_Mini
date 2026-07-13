package com.pwb.backend.modules.liveroom.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.liveroom.chat")
public class ChatProperties {

    @Min(1)
    private int textRateLimitPerMinute = 30;

    @Min(1)
    private int reactionRateLimitPerMinute = 120;

    @Min(1)
    private int maxTextLength = 200;

    @Min(1)
    private int maxPayloadBytes = 1024;

    @NotEmpty
    private Set<@NotBlank String> allowedEmojis = new LinkedHashSet<>(Set.of("\uD83D\uDD25", "\uD83D\uDC4D", "\uD83D\uDC4F", "\uD83D\uDCAF"));

    @NotBlank
    private String chatDestinationPattern = "^/app/rooms/[^/]+/chat$";

    @NotBlank
    private String chatBroadcastDestination = "/topic/rooms/%s/chat";

    @NotBlank
    private String chatRateLimitUserDestination = "/queue/rooms/chat";
}
