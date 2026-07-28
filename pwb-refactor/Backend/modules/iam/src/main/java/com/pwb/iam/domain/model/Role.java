package com.pwb.iam.domain.model;

public final class Role extends BaseEntity {

    private final RoleName name;
    private String description;

    private Role(RoleName name, String description) {
        this.name = name;
        this.description = description;
    }

    public static Role of(RoleName name, String description) {
        if (name == null) {
            throw new IllegalArgumentException("role name must not be null");
        }
        return new Role(name, description);
    }

    public static Role defaultUserRole() {
        return new Role(RoleName.USER, "Default user role");
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
