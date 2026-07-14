package com.pwb.backend.modules.iam.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.oauth.google")
@Getter
@Setter
public class GoogleOAuthProperties {

    private List<String> clientIds = new ArrayList<>();
}