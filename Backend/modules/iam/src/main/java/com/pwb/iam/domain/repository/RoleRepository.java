package com.pwb.iam.domain.repository;

import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;

import java.util.Optional;

public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findByName(RoleName name);
}
