package com.pwb.backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void trustedProxyFirstXForwardedForUsed() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,::1,10.0.0.1");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");
        assertEquals("203.0.113.7", resolver.resolve(req));
    }

    @Test
    void trustedProxyWithoutHeaderFallsBackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        assertEquals("127.0.0.1", resolver.resolve(req));
    }

    @Test
    void untrustedProxyIgnoresXForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("8.8.8.8");
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7");
        assertEquals("8.8.8.8", resolver.resolve(req));
    }

    @Test
    void unknownHeaderStringIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("X-Forwarded-For")).thenReturn("unknown");
        assertEquals("127.0.0.1", resolver.resolve(req));
    }

    @Test
    void nullRequestReturnsUnknown() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertEquals("Unknown", resolver.resolve(null));
    }

    @Test
    void blankRemoteAddrReturnsUnknown() {
        ClientIpResolver resolver = new ClientIpResolver("");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("");
        assertEquals("Unknown", resolver.resolve(req));
    }

    @Test
    void emptyTrustedProxiesUsesDefaults() {
        ClientIpResolver resolver = new ClientIpResolver("");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7");
        assertEquals("203.0.113.7", resolver.resolve(req));
    }

    @Test
    void loopbackRangeTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.5");
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7");
        assertEquals("203.0.113.7", resolver.resolve(req));
    }
}