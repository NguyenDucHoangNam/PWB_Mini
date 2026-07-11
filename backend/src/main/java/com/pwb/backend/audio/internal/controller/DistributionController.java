package com.pwb.backend.audio.internal.controller;

import com.pwb.backend.audio.internal.api.DistributeDemoRequest;
import com.pwb.backend.audio.internal.api.DistributeDemoResponse;
import com.pwb.backend.audio.internal.api.DistributionListResponse;
import com.pwb.backend.audio.internal.helper.CurrentUserResolver;
import com.pwb.backend.audio.internal.service.DistributionService;
import com.pwb.backend.audio.internal.service.RecipientAutocompleteService;
import com.pwb.backend.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/demos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER_PRO')")
public class DistributionController {

  private final DistributionService distributionService;
  private final RecipientAutocompleteService autocompleteService;
  private final CurrentUserResolver currentUserResolver;

  @PostMapping("/{demoId}/distribute")
  public ResponseEntity<ApiResponse<DistributeDemoResponse>> distribute(
      @RequestHeader("Authorization") String authHeader,
      @PathVariable String demoId,
      @Valid @RequestBody DistributeDemoRequest request) {
    String userId = currentUserResolver.requireUserId(authHeader);
    log.info("SHARE_DEMO_REQUEST demoId={} userId={} recipient={}",
        demoId, userId, request.recipientEmail());
    DistributeDemoResponse response = distributionService.distribute(userId, demoId, request);
    return ResponseEntity.status(201)
        .body(ApiResponse.success("Demo distributed", response));
  }

  @GetMapping("/{demoId}/distributions")
  public ResponseEntity<ApiResponse<DistributionListResponse>> list(
      @RequestHeader("Authorization") String authHeader,
      @PathVariable String demoId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean includeRevoked) {
    String userId = currentUserResolver.requireUserId(authHeader);
    DistributionListResponse response = distributionService.listDistributions(
        userId, demoId, page, size, includeRevoked);
    return ResponseEntity.ok(ApiResponse.success("Distributions retrieved", response));
  }

  @GetMapping("/recipients/suggest")
  public ResponseEntity<ApiResponse<List<String>>> suggest(
      @RequestHeader("Authorization") String authHeader,
      @RequestParam("q") String q) {
    String userId = currentUserResolver.requireUserId(authHeader);
    List<String> results = autocompleteService.suggest(userId, q);
    return ResponseEntity.ok(ApiResponse.success("Suggestions retrieved", results));
  }
}
