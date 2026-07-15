package com.pwb.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // TODO(refactor): Implement both methods below using UserRepository once auth flow is wired.
    // Currently the IAM module is being redesigned, so lookups are stubbed to fail fast and
    // surface the missing implementation instead of silently returning null.

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        throw new UsernameNotFoundException("UserRepository not yet implemented. Email: " + email);
    }

    public UserDetails loadUserById(UUID id) {
        throw new UsernameNotFoundException("UserRepository not yet implemented. ID: " + id);
    }
}
