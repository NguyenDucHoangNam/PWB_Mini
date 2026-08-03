package com.pwb.iam.infrastructure.persistence.mapper;

import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoleMapperTest {

    private final RoleMapper mapper = new RoleMapper();

    @Test
    @DisplayName("toDomain should map entity to Role")
    void should_map_entity_to_domain() {
        RoleJpaEntity entity = RoleJpaEntity.builder()
                .name("USER")
                .description("Default")
                .build();
        entity.setId(UUID.randomUUID());

        Role role = mapper.toDomain(entity);

        assertThat(role.getId()).isEqualTo(entity.getId());
        assertThat(role.getName()).isEqualTo(RoleName.USER);
        assertThat(role.getDescription()).isEqualTo("Default");
    }

    @Test
    @DisplayName("toDomain with unknown role name throws IllegalArgumentException")
    void should_handle_unknown_role_name() {
        RoleJpaEntity entity = RoleJpaEntity.builder()
                .name("UNKNOWN_ROLE")
                .description("X")
                .build();
        entity.setId(UUID.randomUUID());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toEntity should map Role to RoleJpaEntity")
    void should_map_domain_to_entity() {
        Role role = Role.create(RoleName.ADMIN, "Administrator");

        RoleJpaEntity entity = mapper.toEntity(role);

        assertThat(entity.getName()).isEqualTo("ADMIN");
        assertThat(entity.getDescription()).isEqualTo("Administrator");
    }

    @Test
    @DisplayName("toDomain with null entity returns null")
    void should_handle_null_entity() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("toEntity with null domain returns null")
    void should_handle_null_domain() {
        assertThat(mapper.toEntity(null)).isNull();
    }
}