package com.pwb.backend.iam.internal.interfaces.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.pwb.backend.shared.web.filter.CsrfSupport;
import com.pwb.backend.shared.web.filter.IpRateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final List<String> allowedOrigins;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final IpRateLimitFilter ipRateLimitFilter;
  private final AuthenticationEntryPoint authenticationEntryPoint;
  private final AccessDeniedHandler accessDeniedHandler;

  public SecurityConfig(
      @Value("${app.security.cors.allowed-origins}") String allowedOriginsCsv,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      IpRateLimitFilter ipRateLimitFilter,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler) {
    this.allowedOrigins = parseAllowedOrigins(allowedOriginsCsv);
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.ipRateLimitFilter = ipRateLimitFilter;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  private static List<String> parseAllowedOrigins(String csv) {
    if (csv == null || csv.isBlank()) {
      throw new IllegalStateException(
          "Property 'app.security.cors.allowed-origins' is required. "
              + "Set it to a comma-separated list of explicit origins "
              + "(e.g. https://app.example.com). Wildcard '*' is not allowed.");
    }
    List<String> origins = java.util.Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
    if (origins.contains("*")) {
      throw new IllegalStateException(
          "Wildcard '*' is not allowed for 'app.security.cors.allowed-origins'.");
    }
    return origins;
  }

  @Bean
  public static GrantedAuthorityDefaults grantedAuthorityDefaults() {
    return new GrantedAuthorityDefaults("");
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CookieCsrfTokenRepository csrfRepo = CsrfSupport.cookieTokenRepository();
    CsrfTokenRequestAttributeHandler csrfHandler = CsrfSupport.requestAttributeHandler();
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfRepo)
            .csrfTokenRequestHandler(csrfHandler)
            .requireCsrfProtectionMatcher(new CookieAuthStateChangingMatcher()))
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers("/ws/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers("/api/v1/demos/shared/**").permitAll()
            .requestMatchers("/api/v1/stream/**").permitAll()
            .requestMatchers("/api/v1/internal/demos/**").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(ipRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(eh -> eh
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  private static final class CookieAuthStateChangingMatcher
      implements org.springframework.security.web.util.matcher.RequestMatcher {
    private static final java.util.Set<String> STATE_CHANGING =
        java.util.Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean matches(HttpServletRequest request) {
      if (!STATE_CHANGING.contains(request.getMethod())) {
        return false;
      }
      return CsrfSupport.isCookieAuthenticated(request);
    }
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control", "Accept-Language"));
    configuration.setExposedHeaders(List.of("Authorization"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
