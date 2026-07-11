package com.pwb.backend.common.config;

import com.pwb.backend.common.security.JwtAuthenticationEntryPoint;
import com.pwb.backend.common.security.JwtAuthenticationFilter;
import com.pwb.backend.common.security.IpRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/v1/auth/check-username",
            "/api/v1/shared/**",
            "/api/v1/demos/shared/{token}/key",
            "/api/v1/demos/shared/{token}/stream/**",
            "/api/v1/demos/shared/{token}/download",
            "/api/v1/health",
            "/api/v1/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    };

    private static final String[] PUBLIC_POST_PATHS = {
            "/api/v1/auth/register",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/login",
            "/api/v1/auth/login/google",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    };

    private static final long HSTS_MAX_AGE_SECONDS = 31536000L;
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   IpRateLimitFilter ipRateLimitFilter,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(ipRateLimitFilter, AuthorizationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}