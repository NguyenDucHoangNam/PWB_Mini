package com.pwb.backend.audio.internal.helper;

import com.pwb.backend.iam.internal.application.helper.JwtPrincipalExtractor;
import com.pwb.backend.iam.internal.infrastructure.repository.UserRepository;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

  private final UserRepository userRepository;
  private final com.pwb.backend.iam.internal.application.service.JwtService jwtService;

  public String requireUserId(String authHeader) {
    String email = JwtPrincipalExtractor.requireEmailFromHeader(authHeader, jwtService);
    return userRepository.findByEmailAndDeletedFalse(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
            "Authenticated user not found"))
        .getId();
  }

  public String requireRole(String authHeader) {
    return JwtPrincipalExtractor.requireRoleFromHeader(authHeader, jwtService);
  }
}
