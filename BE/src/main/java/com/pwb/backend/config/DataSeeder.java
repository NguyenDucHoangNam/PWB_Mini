package com.pwb.backend.config;

import com.pwb.backend.entity.rdbms.Role;
import com.pwb.backend.entity.rdbms.User;
import com.pwb.backend.enums.OAuthProvider;
import com.pwb.backend.enums.UserStatus;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.backend.exception.SeederException;
import com.pwb.backend.repository.rdbms.RoleRepository;
import com.pwb.backend.repository.rdbms.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final SeederProperties seederProperties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seederProperties.isEnabled()) {
            log.info("Data seeder is disabled");
            return;
        }

        log.info("Starting data seeder with {} users", seederProperties.getUsers().size());

        for (SeederProperties.SeedUser seedUser : seederProperties.getUsers()) {
            if (userRepository.existsByEmailAndDeletedFalse(seedUser.getEmail())) {
                log.info("User already exists: {}", seedUser.getEmail());
                continue;
            }

            Role role = roleRepository.findByNameAndDeletedFalse(seedUser.getRole())
                    .orElseThrow(() -> new SeederException(
                            ErrorCode.SEEDER_ROLE_NOT_FOUND, seedUser.getRole()));

            String username = seedUser.getEmail().split("@")[0];

            User user = User.builder()
                    .email(seedUser.getEmail())
                    .username(username)
                    .fullName(seedUser.getFullName())
                    .password(passwordEncoder.encode(seedUser.getPassword()))
                    .role(role)
                    .status(UserStatus.ACTIVE)
                    .oauthProvider(OAuthProvider.LOCAL)
                    .build();

            userRepository.save(user);
            log.info("Seeded user: {} [{}]", seedUser.getEmail(), seedUser.getRole());
        }

        log.info("Data seeder completed");
    }
}
