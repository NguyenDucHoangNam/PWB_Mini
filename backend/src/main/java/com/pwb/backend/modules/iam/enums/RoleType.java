package com.pwb.backend.modules.iam.enums;

import java.util.UUID;

public enum RoleType {
    USER("USER", UUID.fromString("11111111-1111-1111-1111-111111111111")),
    PRO("PRO", UUID.fromString("33333333-3333-3333-3333-333333333333")),
    ADMIN("ADMIN", UUID.fromString("22222222-2222-2222-2222-222222222222"));

    private final String code;
    private final UUID roleId;

    RoleType(String code, UUID roleId) {
        this.code = code;
        this.roleId = roleId;
    }

    public String code() {
        return code;
    }

    public UUID roleId() {
        return roleId;
    }
}