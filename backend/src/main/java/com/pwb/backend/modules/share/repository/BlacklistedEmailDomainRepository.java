package com.pwb.backend.modules.share.repository;

import com.pwb.backend.modules.share.entity.BlacklistedEmailDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlacklistedEmailDomainRepository extends JpaRepository<BlacklistedEmailDomain, UUID> {

    List<BlacklistedEmailDomain> findAllByDeletedAtIsNull();
}