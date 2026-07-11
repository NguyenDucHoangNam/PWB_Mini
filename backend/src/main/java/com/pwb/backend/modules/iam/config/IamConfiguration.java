package com.pwb.backend.modules.iam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LoginProperties.class, GoogleOAuthProperties.class})
public class IamConfiguration {
}
