package com.pwb.backend.service.impl;

import com.pwb.backend.entity.rdbms.Role;
import com.pwb.backend.repository.rdbms.RoleRepository;
import com.pwb.backend.service.RoleLookupService;
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

    private final RoleRepository roleRepository;

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${app.role.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Override
    public Role requireRole(String name) {
        CacheEntry entry = cache.get(name);
        Instant now = Instant.now();
        if (entry != null && entry.expiresAt.isAfter(now)) {
            return entry.role;
        }
        Role role = roleRepository.findByNameAndDeletedFalse(name)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + name));
        cache.put(name, new CacheEntry(role, now.plus(Duration.ofSeconds(Math.max(1L, cacheTtlSeconds)))));
        log.debug("Role cached: name={} ttl={}s", name, cacheTtlSeconds);
        return role;
    }

    private record CacheEntry(Role role, Instant expiresAt) { }
}
