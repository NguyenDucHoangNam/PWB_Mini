package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "pwb.iam.avatar")
public class AvatarProperties {

    private long maxSizeBytes = 5L * 1024 * 1024;

    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");

    private Duration urlTtl = Duration.ofMinutes(15);

    public Set<String> allowedContentTypeSet() {
        return Set.copyOf(allowedContentTypes);
    }
}
