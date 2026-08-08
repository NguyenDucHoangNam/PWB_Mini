package com.pwb.bootstrap.security;

import com.pwb.iam.infrastructure.security.JwtAuthenticationFilter;
import com.pwb.iam.infrastructure.security.RestAccessDeniedHandler;
import com.pwb.iam.infrastructure.security.RestAuthenticationEntryPoint;
import com.pwb.iam.infrastructure.security.SecurityConfig;
import com.pwb.iam.infrastructure.config.SecurityProperties;
import com.pwb.web.filter.HttpRateLimitFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the rate limiter sits in the security chain — the single assumption per-account rate limiting
 * rests on, and one no individual module can check: the chain is assembled from a config in {@code iam}
 * and a filter from {@code shared-web}.
 *
 * <p>Two boundaries have to hold at once, and each fails silently on its own:
 *
 * <ul>
 *   <li><b>After the JWT filter.</b> Ahead of it the security context is still empty, so every request
 *       looks anonymous and the limiter falls back to the client address for everybody — exactly the
 *       behaviour this replaced. Nothing throws; the buckets quietly go back to being per-address. That
 *       is how the filter behaved for its whole previous life as a servlet filter registered at
 *       {@code HIGHEST_PRECEDENCE + 20}, far ahead of the chain Spring Boot installs at -100.</li>
 *   <li><b>Before authorization.</b> Past {@link AuthorizationFilter} a request with a missing or forged
 *       token is rejected before it is ever counted, so an unauthenticated flood stops being rate
 *       limited at all — the case where a limiter matters most.</li>
 * </ul>
 *
 * <p>There is a second reason to build the chain in a test rather than trust it: {@code addFilterAfter}
 * resolves the reference filter's position from a registry populated by the earlier {@code addFilterBefore}
 * call, and throws {@code IllegalArgumentException} when it cannot. That failure happens at context
 * startup, so getting it wrong does not produce a subtly wrong limit — it stops the application booting.
 */
@SpringBootTest(classes = SecurityFilterOrderTest.TestApp.class)
@DisplayName("Security chain – where the rate limiter sits")
class SecurityFilterOrderTest {

    /**
     * Deliberately not the real {@code PwbApplication}: that one drags in JPA, Redis, Kafka and Flyway,
     * none of which say anything about filter order and all of which need a live server to start.
     */
    @Configuration
    @Import(SecurityConfig.class)
    @ImportAutoConfiguration({
            SecurityAutoConfiguration.class,
            // Spring MVC comes along because SecurityConfig matches its public endpoints by path string,
            // and those become MvcRequestMatchers, which refuse to build without MVC's
            // HandlerMappingIntrospector in the same context.
            WebMvcAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class
    })
    static class TestApp {
    }

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private HttpRateLimitFilter httpRateLimitFilter;

    @MockitoBean
    private RestAuthenticationEntryPoint authenticationEntryPoint;

    @MockitoBean
    private RestAccessDeniedHandler accessDeniedHandler;

    @MockitoBean
    private SecurityProperties securityProperties;

    @Autowired
    private SecurityFilterChain chain;

    private int indexOf(Class<?> filterType) {
        List<Filter> filters = chain.getFilters();
        for (int i = 0; i < filters.size(); i++) {
            if (filterType.isInstance(filters.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("the rate limiter is part of the chain at all")
    void should_install_rate_limiter_in_the_security_chain() {
        assertThat(indexOf(HttpRateLimitFilter.class))
                .as("must be inside the security chain, not a standalone servlet filter ahead of it")
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("it runs after the JWT filter, so the principal is already resolved")
    void should_run_after_authentication() {
        assertThat(indexOf(HttpRateLimitFilter.class))
                .as("running before the JWT filter would silently make every bucket per-address again")
                .isGreaterThan(indexOf(JwtAuthenticationFilter.class));
    }

    @Test
    @DisplayName("it runs before authorization, so unauthenticated floods are still counted")
    void should_run_before_authorization() {
        assertThat(indexOf(HttpRateLimitFilter.class))
                .as("running after authorization would let tokenless requests escape the limiter")
                .isLessThan(indexOf(AuthorizationFilter.class));
    }
}
