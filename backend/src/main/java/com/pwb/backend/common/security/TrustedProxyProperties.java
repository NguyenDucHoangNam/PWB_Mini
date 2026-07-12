package com.pwb.backend.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security.trusted-proxies")
@Getter
@Setter
public class TrustedProxyProperties {

    private boolean trustForwardedHeaders = false;

    private List<String> cidrs = new ArrayList<>();
}
