package com.pwb.backend.modules.iam.config;

import com.pwb.backend.common.security.captcha.TurnstileProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LoginProperties.class, GoogleOAuthProperties.class, TurnstileProperties.class})
public class IamConfiguration {
}
