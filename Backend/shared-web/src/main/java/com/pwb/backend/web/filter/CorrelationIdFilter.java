package com.pwb.backend.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveOrGenerate(request);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private String resolveOrGenerate(HttpServletRequest request) {
        String headerValue = request.getHeader(HEADER_CORRELATION_ID);
        if (headerValue == null || headerValue.isBlank()) {
            headerValue = request.getHeader(HEADER_REQUEST_ID);
        }
        if (headerValue == null || headerValue.isBlank()) {
            headerValue = UUID.randomUUID().toString();
        }
        return headerValue;
    }
}
