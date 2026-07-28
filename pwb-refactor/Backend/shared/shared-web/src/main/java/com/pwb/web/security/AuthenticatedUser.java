package com.pwb.web.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {
    private Long userId;
    private String email;
    private Set<String> authorities;
    private boolean isOAuthUser;
}
