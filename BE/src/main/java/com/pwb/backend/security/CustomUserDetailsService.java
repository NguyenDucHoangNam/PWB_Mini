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

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        throw new UsernameNotFoundException("UserRepository not yet implemented. Email: " + email);
    }

    public UserDetails loadUserById(UUID id) {
        throw new UsernameNotFoundException("UserRepository not yet implemented. ID: " + id);
    }
}
