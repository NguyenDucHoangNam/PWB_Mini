package com.pwb.iam.infrastructure.service;

import com.pwb.iam.core.model.Role;
import com.pwb.iam.core.model.RoleName;

public interface RoleLookupService {

    Role requireRole(RoleName roleName);
}
