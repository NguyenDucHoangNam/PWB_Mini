package com.pwb.backend.common.config;

import com.pwb.backend.common.security.RoleSandboxFilter;
import com.pwb.backend.common.security.TrustedProxyProperties;
import com.pwb.backend.common.security.cdn.OriginVerifyFilter;
import com.pwb.backend.common.security.jwt.JwtAuthenticationEntryPoint;
import com.pwb.backend.common.security.jwt.JwtAuthenticationFilter;
import com.pwb.backend.common.security.ratelimit.IpRateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/v1/auth/check-username",
            "/api/v1/shared/**",
            "/api/v1/demos/shared/{token}/key",
            "/api/v1/demos/shared/{token}/stream/**",
            "/api/v1/demos/shared/{token}/download",
            "/api/v1/demos/shared/**",
            "/api/v1/stream/keys/**",
            "/api/v1/stream/*/playlist.m3u8",
            "/api/v1/stream/*/playlist-signature",
            "/api/v1/rooms/*/playback",
            "/api/v1/health",
            "/swagger-ui/**",
            "/swagger-ui.html"
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
            "/api/v1/auth/reset-password",
            "/api/v1/demos/shared/*/track-play",
            "/api/v1/internal/cdn-events",
            "/api/v1/rooms/*/join"
    };

    private static final long HSTS_MAX_AGE_SECONDS = 31536000L;
    private static final int BCRYPT_STRENGTH = 12;

    @Value("${app.security.actuator.public:false}")
    private boolean actuatorPublic;

    @Value("${springdoc.api-docs.path:/v3/api-docs}")
    private String apiDocsPath;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   IpRateLimitFilter ipRateLimitFilter,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RoleSandboxFilter roleSandboxFilter,
                                                   JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   OriginVerifyFilter originVerifyFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint));

        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll();
            auth.requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll();
            auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
            if (actuatorPublic) {
                auth.requestMatchers("/api/v1/actuator/**").permitAll();
            } else {
                auth.requestMatchers("/api/v1/actuator/**").hasRole("ADMIN");
            }
            auth.requestMatchers(apiDocsPath, apiDocsPath + "/**").hasRole("ADMIN");
            auth.anyRequest().authenticated();
        });

        http.addFilterBefore(originVerifyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(ipRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(roleSandboxFilter, JwtAuthenticationFilter.class);

        return http.build();
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
