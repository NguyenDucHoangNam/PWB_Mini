package com.pwb.backend.audio.internal.interfaces.controller;

import com.pwb.backend.shared.web.security.CurrentUserResolver;
import com.pwb.backend.audio.internal.application.service.RevokeService;
import com.pwb.backend.shared.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/demos/distributions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER_PRO')")
public class RevokeController {

  private final RevokeService revokeService;
  private final CurrentUserResolver currentUserResolver;

  @PostMapping("/{distributionId}/revoke")
  public ResponseEntity<ApiResponse<Void>> revoke(
      @RequestHeader("Authorization") String authHeader,
      @PathVariable("distributionId") String distributionId,
      HttpServletRequest request) {
    String userId = currentUserResolver.requireUserId(authHeader);
    log.info("REVOKE_REQUEST distributionId={} userId={}", distributionId, userId);
    revokeService.revoke(distributionId, userId, request);
    return ResponseEntity.ok(ApiResponse.success("Thu hồi quyền truy cập liên kết chia sẻ thành công"));
  }
}