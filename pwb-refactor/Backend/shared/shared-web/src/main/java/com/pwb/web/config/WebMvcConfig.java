package com.pwb.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.web.filter.CorrelationIdFilter;
import com.pwb.web.filter.HttpRateLimitFilter;
import com.pwb.web.filter.HttpRateLimitService;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.CurrentClientIpArgumentResolver;
import com.pwb.web.security.CurrentUserAgentArgumentResolver;
import com.pwb.web.security.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final CurrentClientIpArgumentResolver currentClientIpArgumentResolver;
    private final CurrentUserAgentArgumentResolver currentUserAgentArgumentResolver;

    @Value("${pwb.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Correlation-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
        resolvers.add(currentClientIpArgumentResolver);
        resolvers.add(currentUserAgentArgumentResolver);
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        registration.setName("correlationIdFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<HttpRateLimitFilter> httpRateLimitFilter(
            HttpRateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper,
            MessageResolver messageResolver
    ) {
        FilterRegistrationBean<HttpRateLimitFilter> registration = new FilterRegistrationBean<>(
                new HttpRateLimitFilter(rateLimitService, rateLimitProperties, objectMapper, messageResolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        registration.setName("httpRateLimitFilter");
        return registration;
    }
}