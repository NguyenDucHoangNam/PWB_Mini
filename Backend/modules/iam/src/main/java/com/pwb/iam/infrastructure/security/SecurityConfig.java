package com.pwb.iam.infrastructure.security;

import com.pwb.iam.infrastructure.config.SecurityProperties;
import com.pwb.web.filter.HttpRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] DEFAULT_PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final HttpRateLimitFilter httpRateLimitFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final SecurityProperties securityProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] publicEndpoints = resolvePublicEndpoints();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Immediately after the JWT filter, and this position is the whole point. The rate
                // limiter used to be a servlet filter running ahead of this chain, where the security
                // context is still empty — so it could only ever count by client address, and a shared
                // address meant a shared bucket for everyone behind it. Here the principal is already
                // resolved, so an authenticated caller gets a bucket of their own.
                //
                // It still has to sit well before authorization, which happens at the end of the chain:
                // a request with a missing or forged token must be counted on its way to the 401 rather
                // than escape the limiter entirely.
                .addFilterAfter(httpRateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private String[] resolvePublicEndpoints() {
        var configured = securityProperties.getPublicEndpoints();
        var resolved = new java.util.LinkedHashSet<String>();
        if (configured != null && !configured.isEmpty()) {
            resolved.addAll(configured);
        }
        for (String endpoint : DEFAULT_PUBLIC_ENDPOINTS) {
            resolved.add(endpoint);
        }
        return resolved.toArray(String[]::new);
    }
}