package com.pwb.iam.infrastructure.service;

import com.pwb.iam.core.model.Role;
import com.pwb.iam.core.model.RoleName;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;

public interface RoleLookupService {

    Role requireRole(RoleName roleName);

    RoleJpaEntity requireRoleEntity(RoleName roleName);
}
