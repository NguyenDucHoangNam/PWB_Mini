package com.pwb.backend.repository.rdbms;

import com.pwb.backend.entity.rdbms.Role;
import com.pwb.backend.repository.BaseRepository;

import java.util.Optional;

public interface RoleRepository extends BaseRepository<Role> {

    Optional<Role> findByNameAndDeletedFalse(String name);
}
