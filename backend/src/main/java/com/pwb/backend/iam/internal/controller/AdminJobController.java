package com.pwb.backend.iam.internal.controller;

import com.pwb.backend.iam.api.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.iam.internal.service.AccountLifecycleService;
import com.pwb.backend.iam.internal.service.JwtEpochService;
import com.pwb.backend.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

  private final AccountLifecycleService accountLifecycleService;
  private final JwtEpochService jwtEpochService;
  private final MessageSource messageSource;

  @PostMapping("/trigger-anonymization")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ResponseEntity<ApiResponse<TriggerAnonymizationResponse>> triggerAnonymization(
      HttpServletRequest request) {
    TriggerAnonymizationResponse response = accountLifecycleService.triggerAnonymization();
    String message = messageSource.getMessage(
        "admin.anonymization.success", null, request.getLocale());
    return ResponseEntity.ok(ApiResponse.success(message, response));
  }

  /**
   * Bump the JWT epoch, invalidating all outstanding access tokens.
   * Use after rotating the JWT signing secret.
   */
  @PostMapping("/rotate-jwt-epoch")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ResponseEntity<ApiResponse<Map<String, Long>>> rotateJwtEpoch() {
    long epoch = jwtEpochService.rotateEpoch();
    return ResponseEntity.ok(ApiResponse.success(
        "JWT epoch rotated, all existing access tokens invalidated",
        Map.of("epoch", epoch)));
  }
}
