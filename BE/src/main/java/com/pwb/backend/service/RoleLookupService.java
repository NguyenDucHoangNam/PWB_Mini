package com.pwb.backend.service;

import com.pwb.backend.entity.rdbms.Role;
import com.pwb.backend.repository.rdbms.RoleRepository;

public interface RoleLookupService {

    Role requireRole(String name);
}
