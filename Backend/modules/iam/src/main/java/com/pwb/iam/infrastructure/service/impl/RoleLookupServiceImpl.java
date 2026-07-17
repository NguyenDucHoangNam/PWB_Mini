package com.pwb.iam.infrastructure.service.impl;

import com.pwb.backend.exception.BusinessException;
import com.pwb.backend.exception.ErrorCode;
import com.pwb.iam.core.model.Role;
import com.pwb.iam.core.model.RoleName;
import com.pwb.iam.infrastructure.persistence.mapper.RoleMapper;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import com.pwb.iam.infrastructure.service.RoleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleLookupServiceImpl implements RoleLookupService {

    private final RoleJpaRepository roleJpaRepository;
    private final RoleMapper roleMapper;

    @Value("${app.role.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    private final ConcurrentMap<RoleName, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public Role requireRole(RoleName roleName) {
        CacheEntry entry = cache.get(roleName);
        Instant now = Instant.now();
        if (entry != null && entry.expiresAt.isAfter(now)) {
            return entry.role;
        }
        Role role = roleJpaRepository.findByNameAndDeletedFalse(roleName.name())
                .map(roleMapper::toDomain)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEEDER_ROLE_NOT_FOUND));
        cache.put(roleName, new CacheEntry(role, now.plus(Duration.ofSeconds(Math.max(1L, cacheTtlSeconds)))));
        log.debug("Role cached: name={} ttl={}s", roleName, cacheTtlSeconds);
        return role;
    }

    private record CacheEntry(Role role, Instant expiresAt) { }
}
