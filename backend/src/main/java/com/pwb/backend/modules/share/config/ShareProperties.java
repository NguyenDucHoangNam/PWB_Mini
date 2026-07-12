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
    private int dailyRecipientsLimit = 100;

    @Min(1)
    private int dailyCountLimit = 500;

    @Min(60)
    private long distributionCacheTtlSeconds = 86400L;

    private boolean blacklistDomainCheckEnabled = true;

    private String shareLinkBaseUrl = "https://pwbmini.com/shared/";

    private long blacklistCacheTtlSeconds = 3600L;
}