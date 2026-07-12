package com.pwb.backend.modules.iam.service.impl;

import com.pwb.backend.modules.iam.repository.UserRepository;
import com.pwb.backend.modules.iam.service.UsernameAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsernameAvailabilityServiceImpl implements UsernameAvailabilityService {

    private final UserRepository userRepository;

    @Override
    public boolean isAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }
}