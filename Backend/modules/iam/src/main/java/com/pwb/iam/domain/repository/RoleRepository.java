package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.Role;

import java.util.Optional;

public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findByName(String name);
}
