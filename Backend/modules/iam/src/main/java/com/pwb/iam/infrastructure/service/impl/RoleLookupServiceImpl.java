package com.pwb.iam.infrastructure.service.impl;

import com.pwb.kernel.exception.BusinessException;
import com.pwb.iam.core.exception.IamErrorCode;

import com.pwb.iam.core.model.Role;
import com.pwb.iam.core.model.RoleName;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
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
    private final ConcurrentMap<RoleName, RoleJpaEntity> entityCache = new ConcurrentHashMap<>();

    @Override
    public Role requireRole(RoleName roleName) {
        CacheEntry entry = cache.get(roleName);
        Instant now = Instant.now();
        if (entry != null && entry.expiresAt.isAfter(now)) {
            return entry.role;
        }
        RoleJpaEntity entity = loadEntity(roleName);
        Role role = roleMapper.toDomain(entity);
        cache.put(roleName, new CacheEntry(role, now.plus(Duration.ofSeconds(Math.max(1L, cacheTtlSeconds)))));
        log.debug("Role cached: name={} ttl={}s", roleName, cacheTtlSeconds);
        return role;
    }

    @Override
    public RoleJpaEntity requireRoleEntity(RoleName roleName) {
        RoleJpaEntity cached = entityCache.get(roleName);
        if (cached != null) {
            return cached;
        }
        RoleJpaEntity loaded = loadEntity(roleName);
        entityCache.put(roleName, loaded);
        return loaded;
    }

    private RoleJpaEntity loadEntity(RoleName roleName) {
        return roleJpaRepository.findByNameAndDeletedFalse(roleName.name())
                .orElseThrow(() -> new BusinessException(IamErrorCode.SEEDER_ROLE_NOT_FOUND));
    }

    private record CacheEntry(Role role, Instant expiresAt) { }
}



