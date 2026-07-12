package com.pwb.backend.modules.iam.service;

public interface UsernameAvailabilityService {

    boolean isAvailable(String username);
}