package com.pwb.backend.modules.iam.seeder;

import com.pwb.backend.common.util.PasswordHasher;
import com.pwb.backend.modules.iam.config.IamSeederProperties;
import com.pwb.backend.modules.iam.config.IamSeederProperties.SeedUser;
import com.pwb.backend.modules.iam.enums.RoleType;
import com.pwb.backend.modules.iam.enums.UserStatus;
import com.pwb.backend.modules.iam.model.Role;
import com.pwb.backend.modules.iam.model.User;
import com.pwb.backend.modules.iam.repository.RoleRepository;
import com.pwb.backend.modules.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class IamDataSeeder implements CommandLineRunner {

    private final IamSeederProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.isEnabled()) {
            log.debug("IAM seeder is disabled (app.iam.seeder.enabled=false)");
            return;
        }

        if (properties.getUsers() == null || properties.getUsers().isEmpty()) {
            log.warn("IAM seeder is enabled but no users are configured");
            return;
        }

        Map<RoleType, Role> roleCache = new EnumMap<>(RoleType.class);
        int created = 0;
        int skipped = 0;

        for (SeedUser seed : properties.getUsers()) {
            if (isInvalid(seed)) {
                log.warn("Skipping invalid seed user entry: {}", seed);
                skipped++;
                continue;
            }

            if (userRepository.existsByEmail(seed.getEmail())) {
                log.info("Skip seed user (already exists): email={}", seed.getEmail());
                skipped++;
                continue;
            }

            RoleType roleType = resolveRoleType(seed.getRole());
            if (roleType == null) {
                log.warn("Skipping seed user with unknown role '{}': email={}",
                        seed.getRole(), seed.getEmail());
                skipped++;
                continue;
            }

            Role role = roleCache.computeIfAbsent(roleType,
                    rt -> roleRepository.findByCode(rt.code()).orElse(null));

            if (role == null) {
                log.error("Role '{}' missing in database; run Flyway migrations before seeding",
                        roleType.code());
                skipped++;
                continue;
            }

            User user = User.newPending(seed.getEmail(),
                    passwordHasher.hash(seed.getPassword()),
                    seed.getFullName(),
                    role);
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerifiedAt(Instant.now());

            userRepository.save(user);
            log.info("Seeded user: email={} role={} userId={}",
                    seed.getEmail(), roleType.code(), user.getId());
            created++;
        }

        log.info("IAM seeding completed: created={} skipped={}", created, skipped);
    }

    private boolean isInvalid(SeedUser seed) {
        return seed == null
                || isBlank(seed.getEmail())
                || isBlank(seed.getFullName())
                || isBlank(seed.getPassword())
                || isBlank(seed.getRole());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RoleType resolveRoleType(String code) {
        if (code == null) {
            return null;
        }
        for (RoleType type : RoleType.values()) {
            if (type.code().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}