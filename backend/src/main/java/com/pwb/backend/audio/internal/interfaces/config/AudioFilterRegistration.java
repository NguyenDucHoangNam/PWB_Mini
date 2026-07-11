package com.pwb.backend.audio.internal.interfaces.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AudioFilterRegistration {

  @Bean
  public FilterRegistrationBean<AudioRateLimitFilter> audioRateLimitFilterRegistration(
      AudioRateLimitFilter filter) {
    FilterRegistrationBean<AudioRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    registration.addUrlPatterns(
        "/api/v1/demos/*",
        "/api/v1/voice-tags/*",
        "/api/v1/stream/*",
        "/api/v1/internal/demos/*");
    registration.setName("audioRateLimitFilter");
    return registration;
  }
}