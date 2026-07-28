package com.pwb.iam.infrastructure.config;

import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
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
public class UserSeeder implements ApplicationRunner {

    private final SeederProperties seederProperties;
    private final UserJpaRepository userJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seederProperties.isEnabled()) {
            log.info("IAM user seeder is disabled");
            return;
        }

        log.info("IAM user seeder started — {} users configured", seederProperties.getUsers().size());

        int created = 0;
        int skipped = 0;

        for (SeederProperties.SeedUser seedUser : seederProperties.getUsers()) {
            if (userJpaRepository.existsByEmailAndDeletedFalse(seedUser.getEmail())) {
                log.debug("Seed user already exists: email={}", seedUser.getEmail());
                skipped++;
                continue;
            }

            RoleName roleName;
            try {
                roleName = RoleName.valueOf(seedUser.getRole());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role '{}' for seed user email={}, skipping", seedUser.getRole(), seedUser.getEmail());
                skipped++;
                continue;
            }

            RoleJpaEntity roleEntity = roleJpaRepository.findByNameAndDeletedFalse(roleName.name())
                    .orElse(null);
            if (roleEntity == null) {
                log.warn("Role '{}' not found in DB for seed user email={}, skipping", roleName, seedUser.getEmail());
                skipped++;
                continue;
            }

            String username = deriveUsername(seedUser.getEmail());
            String hashedPassword = passwordEncoder.encode(seedUser.getPassword());

            UserJpaEntity entity = UserJpaEntity.builder()
                    .username(username)
                    .email(seedUser.getEmail())
                    .password(hashedPassword)
                    .fullName(seedUser.getFullName())
                    .status(UserStatus.ACTIVE)
                    .role(roleEntity)
                    .oauthProvider(OAuthProvider.LOCAL)
                    .build();

            userJpaRepository.save(entity);
            log.info("Seed user created: email={}, role={}", seedUser.getEmail(), roleName);
            created++;
        }

        log.info("IAM user seeder finished — created={}, skipped={}", created, skipped);
    }

    private String deriveUsername(String email) {
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }
}
