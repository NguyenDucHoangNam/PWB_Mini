package com.pwb.backend.common.util;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {

    private static final String MDC_TRACE_KEY = "traceId";
    private static final String MDC_REQUEST_KEY = "requestId";

    private TraceContext() {
    }

    public static String currentTraceId() {
        String fromMdc = MDC.get(MDC_TRACE_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String requestId = MDC.get(MDC_REQUEST_KEY);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    public static String ensureTraceId() {
        String existing = currentTraceId();
        MDC.put(MDC_TRACE_KEY, existing);
        return existing;
    }
}
