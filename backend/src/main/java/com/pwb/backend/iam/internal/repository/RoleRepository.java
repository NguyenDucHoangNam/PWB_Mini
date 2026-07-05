package com.pwb.backend.iam.internal.repository;

import com.pwb.backend.iam.internal.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {

  Optional<Role> findByName(String name);
}
