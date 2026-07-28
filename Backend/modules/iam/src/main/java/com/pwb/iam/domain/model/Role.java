package com.pwb.iam.domain.model;

import lombok.Getter;

@Getter
public final class Role extends BaseEntity {

    private final RoleName roleId;
    private final RoleName name;
    private String description;

    private Role(RoleName name, String description) {
        this.roleId = name;
        this.name = name;
        this.description = description;
    }

    public static Role of(RoleName name, String description) {
        return new Role(name, description);
    }

    public static Role defaultUserRole() {
        return new Role(RoleName.USER, "Default user role");
    }

    public void updateDescription(String description) {
        this.description = description;
        touch();
    }
}
