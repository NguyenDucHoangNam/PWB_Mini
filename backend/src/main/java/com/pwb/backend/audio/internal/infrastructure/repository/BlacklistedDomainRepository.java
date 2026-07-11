package com.pwb.backend.audio.internal.infrastructure.repository;

import com.pwb.backend.audio.internal.domain.model.BlacklistedDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlacklistedDomainRepository extends JpaRepository<BlacklistedDomain, String> {

  Optional<BlacklistedDomain> findByDomainIgnoreCase(String domain);
}
