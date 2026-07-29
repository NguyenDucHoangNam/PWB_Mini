package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSeederService {

    private final SeederProperties seederProperties;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    @Transactional
    public void seedUsers() {
        if (!seederProperties.isEnabled()) {
            log.debug("User seeder is disabled, skipping");
            return;
        }

        log.info("Starting user seeder, {} users configured", seederProperties.getUsers().size());

        for (SeederProperties.SeedUser seedUser : seederProperties.getUsers()) {
            try {
                seedUserIfNotExists(seedUser);
            } catch (Exception e) {
                log.error("Failed to seed user email={}: {}", seedUser.getEmail(), e.getMessage());
            }
        }

        log.info("User seeder completed");
    }

    private void seedUserIfNotExists(SeederProperties.SeedUser seedUser) {
        if (userRepository.existsByEmail(seedUser.getEmail())) {
            log.debug("User already exists, skipping: email={}", seedUser.getEmail());
            return;
        }

        if (userRepository.existsByUsername(seedUser.getUsername())) {
            log.debug("Username already exists, skipping: username={}", seedUser.getUsername());
            return;
        }

        RoleName roleName = parseRoleName(seedUser.getRole());
        UserStatus userStatus = parseUserStatus(seedUser.getStatus());

        Password hashedPassword = Password.fromHash(passwordHasher.hash(seedUser.getPassword()));
        EmailAddress email = EmailAddress.of(seedUser.getEmail());

        User user = User.createLocal(
                seedUser.getUsername(),
                email,
                hashedPassword,
                seedUser.getFullName(),
                roleName
        );

        if (userStatus == UserStatus.ACTIVE) {
            user.markActive();
            user.completeProfile(seedUser.getUsername(), seedUser.getFullName());
        }

        userRepository.save(user);
        log.info("Seeded user: email={}, role={}", seedUser.getEmail(), roleName);
    }

    private RoleName parseRoleName(String role) {
        if (role == null || role.isBlank()) {
            return RoleName.USER;
        }
        try {
            return RoleName.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role '{}', defaulting to USER", role);
            return RoleName.USER;
        }
    }

    private UserStatus parseUserStatus(String status) {
        if (status == null || status.isBlank()) {
            return UserStatus.ACTIVE;
        }
        try {
            return UserStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status '{}', defaulting to ACTIVE", status);
            return UserStatus.ACTIVE;
        }
    }
}
