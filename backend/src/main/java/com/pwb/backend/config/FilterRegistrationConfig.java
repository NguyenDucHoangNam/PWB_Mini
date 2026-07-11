package com.pwb.backend.config;

import com.pwb.backend.filter.IpRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<IpRateLimitFilter> ipRateLimitFilterRegistration(IpRateLimitFilter filter) {
        FilterRegistrationBean<IpRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}