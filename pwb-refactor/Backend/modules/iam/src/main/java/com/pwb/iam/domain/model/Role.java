package com.pwb.iam.domain.model;

import com.pwb.shared.domain.DomainBaseEntity;

import java.util.UUID;

public final class Role extends DomainBaseEntity {

    private final UUID id;
    private final RoleName name;
    private String description;

    private Role(UUID id, RoleName name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public static Role of(UUID id, RoleName name, String description) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("role name must not be null");
        }
        return new Role(id, name, description);
    }

    public static Role create(RoleName name, String description) {
        return new Role(UUID.randomUUID(), name, description);
    }

    public static Role defaultUserRole() {
        return new Role(UUID.randomUUID(), RoleName.USER, "Default user role");
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void updateDescription(String description) {
        this.description = description;
        touch();
    }
}
