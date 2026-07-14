package com.pwb.backend.modules.share.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.share")
public class ShareProperties {

    @Min(1)
    private int dailyRecipientsLimit;

    @Min(1)
    private int dailyCountLimit;

    @Min(60)
    private long distributionCacheTtlSeconds;

    private boolean blacklistDomainCheckEnabled;

    private String shareLinkBaseUrl;

    private long blacklistCacheTtlSeconds;
}