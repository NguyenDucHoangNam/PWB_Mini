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
                .allowedHeaders("Authorization", "Content-Type", "X-Correlation-Id", "Accept-Language")
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

    /**
     * Built here but deliberately <em>not</em> installed here — {@code SecurityConfig} places it inside the
     * security chain, just after the JWT filter.
     *
     * <p>It used to run as a servlet filter at {@code HIGHEST_PRECEDENCE + 20}, which put it ahead of the
     * whole security chain (Spring Boot registers that at order -100). Everything therefore looked
     * anonymous to it, and the only identity available was the client address. Rate limiting per account
     * means running after authentication has happened, which means running inside the chain.
     *
     * <p>Position within the chain still matters: after the JWT filter so the principal is there, but well
     * before authorization, so a request with no token or a bad one is counted rather than waved past on
     * its way to a 401.
     */
    @Bean
    public HttpRateLimitFilter httpRateLimitFilter(
            HttpRateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper,
            MessageResolver messageResolver
    ) {
        return new HttpRateLimitFilter(rateLimitService, rateLimitProperties, objectMapper, messageResolver);
    }

    /**
     * Cancels Spring Boot's automatic servlet registration of the bean above. Any {@code Filter} bean is
     * picked up and mapped to {@code /*} by default, so without this the filter would run twice per
     * request from two different positions — and the early copy would be the anonymous one this change
     * exists to get rid of.
     */
    @Bean
    public FilterRegistrationBean<HttpRateLimitFilter> httpRateLimitFilterRegistration(
            HttpRateLimitFilter httpRateLimitFilter
    ) {
        FilterRegistrationBean<HttpRateLimitFilter> registration =
                new FilterRegistrationBean<>(httpRateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}
