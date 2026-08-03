package com.pwb.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ClientIpResolver — extract client IP from request")
class ClientIpResolverTest {

    @Test
    @DisplayName("should_return_unknown_when_request_null")
    void should_return_unknown_when_request_null() {
        String ip = ClientIpResolver.resolve(null, List.of());

        assertThat(ip).isEqualTo("unknown");
    }

    @Test
    @DisplayName("should_return_remote_addr_when_proxy_not_trusted")
    void should_return_remote_addr_when_proxy_not_trusted() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        String ip = ClientIpResolver.resolve(request, List.of());

        assertThat(ip).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("should_return_x_forwarded_for_first_ip_when_proxy_trusted")
    void should_return_x_forwarded_for_first_ip_when_proxy_trusted() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.50");

        String ip = ClientIpResolver.resolve(request, List.of("10.0.0.1"));

        assertThat(ip).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("should_return_first_ip_when_no_comma_in_forwarded_header")
    void should_return_first_ip_when_no_comma_in_forwarded_header() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.20");

        String ip = ClientIpResolver.resolve(request, List.of("10.0.0.1"));

        assertThat(ip).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("should_fall_back_to_remote_addr_when_forwarded_header_blank")
    void should_fall_back_to_remote_addr_when_forwarded_header_blank() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");

        String ip = ClientIpResolver.resolve(request, List.of("10.0.0.1"));

        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("should_return_unknown_when_remote_addr_null")
    void should_return_unknown_when_remote_addr_null() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(null);

        String ip = ClientIpResolver.resolve(request, List.of());

        assertThat(ip).isEqualTo("unknown");
    }
}