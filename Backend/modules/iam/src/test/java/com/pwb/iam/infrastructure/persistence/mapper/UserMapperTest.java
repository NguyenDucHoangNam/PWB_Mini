package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.RoleRepository;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMapperTest {

    @Mock private RoleRepository roleRepository;
    @Mock private RoleJpaRepository roleJpaRepository;

    private UserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UserMapper(roleRepository, roleJpaRepository);
        lenient().when(roleJpaRepository.findByNameAndDeletedFalse(eq("USER")))
                .thenReturn(Optional.of(roleEntity("USER", "Default")));
    }

    private static RoleJpaEntity roleEntity(String name, String description) {
        RoleJpaEntity entity = RoleJpaEntity.builder().name(name).description(description).build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    @Test
    @DisplayName("toEntity should build UserJpaEntity from domain")
    void should_build_entity_from_domain() {
        User user = User.createLocal(
                EmailAddress.of("user@example.com"),
                Password.fromHash("hash-value"),
                "Alice",
                RoleName.USER);

        UserJpaEntity entity = mapper.toEntity(user, null);

        assertThat(entity.getEmail()).isEqualTo("user@example.com");
        assertThat(entity.getPassword()).isEqualTo("hash-value");
        assertThat(entity.getFullName()).isEqualTo("Alice");
        assertThat(entity.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(entity.getOauthProvider()).isEqualTo(OAuthProvider.LOCAL);
    }

    @Test
    @DisplayName("toEntity with existing entity should update fields and reuse")
    void should_update_existing_entity() {
        User user = User.rehydrate(
                UUID.randomUUID(),
                "user@example.com",
                "hash",
                "Alice",
                null,
                null,
                UserStatus.ACTIVE,
                "USER",
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null);

        UserJpaEntity existing = UserJpaEntity.builder()
                .email("old@example.com")
                .password("old")
                .fullName("Old")
                .status(UserStatus.PENDING_VERIFICATION)
                .oauthProvider(OAuthProvider.LOCAL)
                .build();

        UserJpaEntity result = mapper.toEntity(user, existing);

        assertThat(result).isSameAs(existing);
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getFullName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("toDomain should map back to User")
    void should_map_entity_to_domain() {
        RoleJpaEntity role = roleEntity("USER", "Default");
        UserJpaEntity entity = UserJpaEntity.builder()
                .email("user@example.com")
                .password("hash")
                .fullName("Alice")
                .status(UserStatus.ACTIVE)
                .role(role)
                .oauthProvider(OAuthProvider.LOCAL)
                .build();
        entity.setId(UUID.randomUUID());

        User user = mapper.toDomain(entity);

        assertThat(user.getEmail().value()).isEqualTo("user@example.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(RoleName.USER);
    }

    @Test
    @DisplayName("toEntity with null domain returns null")
    void should_return_null_for_null_domain() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    @DisplayName("toDomain with null entity returns null")
    void should_return_null_for_null_entity() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("toEntity with null roleName should leave role unset when role not found")
    void should_handle_null_role_name() {
        org.mockito.Mockito.reset(roleJpaRepository);
        User user = User.rehydrate(
                UUID.randomUUID(),
                "user@example.com",
                "hash",
                "Alice",
                null,
                null,
                UserStatus.ACTIVE,
                null,
                OAuthProvider.LOCAL,
                null,
                null,
                null,
                null);

        UserJpaEntity entity = mapper.toEntity(user, null);

        assertThat(entity.getRole()).isNull();
    }

    @Test
    @DisplayName("toDomain with unknown roleName should produce null role")
    void should_handle_unknown_role_in_domain_mapping() {
        UserJpaEntity entity = UserJpaEntity.builder()
                .email("user@example.com")
                .password("hash")
                .fullName("Alice")
                .status(UserStatus.ACTIVE)
                .oauthProvider(OAuthProvider.LOCAL)
                .build();
        entity.setId(UUID.randomUUID());

        User user = mapper.toDomain(entity);

        assertThat(user.getRole()).isNull();
    }
}