package com.pwb.backend.audio.internal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.backend.audio.internal.service.BruteForceGuardService;
import com.pwb.backend.shared.exception.ErrorCode;
import com.pwb.backend.shared.response.ApiResponse;
import com.pwb.backend.shared.response.ErrorDetail;
import com.pwb.backend.shared.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ListenerAuthFilter extends OncePerRequestFilter {

  private static final Pattern SHARED_PATH = Pattern.compile("^/api/v1/demos/shared/([^/]+)(?:/.*)?$");
  private static final Pattern STREAM_PATH = Pattern.compile("^/api/v1/stream/keys/([^/]+)$");

  private final BruteForceGuardService bruteForceGuardService;
  private final ClientIpResolver clientIpResolver;
  private final ObjectMapper objectMapper;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(SHARED_PATH.matcher(path).matches() || STREAM_PATH.matcher(path).matches());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String ip = clientIpResolver.resolve(request);
    if (bruteForceGuardService.isIpBlocked(ip)) {
      log.warn("Listener IP blocked by brute-force guard: ip={} path={}", ip, request.getRequestURI());
      writeBlocked(response);
      return;
    }
    chain.doFilter(request, response);
  }

  private void writeBlocked(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ErrorDetail detail = new ErrorDetail(
        ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), null, "IP temporarily blocked");
    ApiResponse<Void> body = ApiResponse.error("IP temporarily blocked", List.of(detail));
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}