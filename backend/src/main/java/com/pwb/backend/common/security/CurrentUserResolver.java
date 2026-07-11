package com.pwb.backend.common.security;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.CommonErrorCode;
import com.pwb.backend.common.security.jwt.JwtTypes.AuthenticatedUser;
import com.pwb.backend.common.security.jwt.JwtTypes.JwtAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserResolver {

    public UUID resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        AuthenticatedUser principal = jwtAuth.getPrincipal();
        if (principal == null || principal.userId() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }
}