package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.RoleMapper;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import com.pwb.iam.testsupport.AbstractRepositoryIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoleRepositoryImpl — H2 integration")
class RoleRepositoryImplIT extends AbstractRepositoryIT {

    @Autowired private RoleJpaRepository jpaRepository;
    @Autowired private RoleMapper roleMapper;

    private RoleRepositoryImpl repository;

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        repository = new RoleRepositoryImpl(jpaRepository, roleMapper);
    }

    @Test
    @DisplayName("should_save_role_and_find_by_name")
    void should_save_role_and_find_by_name() {
        Role role = Role.create(RoleName.USER, "Standard user role");

        Role saved = repository.save(role);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo(RoleName.USER);

        Role found = repository.findByName(RoleName.USER).orElseThrow();
        assertThat(found.getDescription()).isEqualTo("Standard user role");
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("should_return_empty_when_role_not_found")
    void should_return_empty_when_role_not_found() {
        assertThat(repository.findByName(RoleName.ADMIN)).isEmpty();
    }

    @Test
    @DisplayName("should_return_empty_when_name_null")
    void should_return_empty_when_name_null() {
        assertThat(repository.findByName(null)).isEmpty();
    }

    @Test
    @DisplayName("should_update_existing_role_when_save_called_twice")
    void should_update_existing_role_when_save_called_twice() {
        Role first = repository.save(Role.create(RoleName.USER, "v1"));
        Role second = repository.save(Role.create(RoleName.USER, "v2"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getDescription()).isEqualTo("v2");
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_skip_deleted_role_in_findByName")
    void should_skip_deleted_role_in_findByName() {
        RoleJpaEntity entity = jpaRepository.save(RoleJpaEntity.builder()
                .name(RoleName.USER.name())
                .description("to delete")
                .build());
        entity.setDeleted(true);
        jpaRepository.saveAndFlush(entity);

        assertThat(repository.findByName(RoleName.USER)).isEmpty();
    }
}