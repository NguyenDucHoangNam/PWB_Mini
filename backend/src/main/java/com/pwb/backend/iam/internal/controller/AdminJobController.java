package com.pwb.backend.iam.internal.controller;

import com.pwb.backend.iam.api.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.iam.internal.service.AuthService;
import com.pwb.backend.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

  private final AuthService authService;

  @PostMapping("/trigger-anonymization")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<TriggerAnonymizationResponse>> triggerAnonymization() {
    TriggerAnonymizationResponse response = authService.triggerAnonymization();
    return ResponseEntity.ok(ApiResponse.success("Kích hoạt chạy tiến trình ẩn danh hóa thành công", response));
  }
}
