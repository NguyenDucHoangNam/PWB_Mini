package com.pwb.iam.infrastructure.security;

import com.pwb.backend.exception.BusinessException;
import com.pwb.iam.core.exception.IamErrorCode;
import com.pwb.iam.core.model.User;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) {
        throw new UnsupportedOperationException(
                "loadUserByUsername is not supported; use loadUserById(UUID) instead");
    }

    public UserDetails loadUserById(UUID userId) {
        User user = userJpaRepository.findByIdAndDeletedFalse(userId)
                .map(userMapper::toDomain)
                .orElseThrow(() -> new BusinessException(IamErrorCode.USER_NOT_FOUND));
        return new CustomUserDetails(user);
    }
}

