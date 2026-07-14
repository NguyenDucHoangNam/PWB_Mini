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
    private int textRateLimitPerMinute;

    @Min(1)
    private int reactionRateLimitPerMinute;

    @Min(1)
    private int maxTextLength;

    @Min(1)
    private int maxPayloadBytes;

    @NotEmpty
    private Set<@NotBlank String> allowedEmojis = new LinkedHashSet<>();

    @NotBlank
    private String chatDestinationPattern;

    @NotBlank
    private String chatBroadcastDestination;

    @NotBlank
    private String chatRateLimitUserDestination;
}
