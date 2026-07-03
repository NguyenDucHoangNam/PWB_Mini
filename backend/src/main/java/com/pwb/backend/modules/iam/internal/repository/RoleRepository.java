package com.pwb.backend.modules.iam.internal.repository;

import com.pwb.backend.modules.iam.internal.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {

  Optional<Role> findByName(String name);
}
