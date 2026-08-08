package com.pwb.iam.application.usecase;

import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminListUsersUseCase {

    Page<AdminUserView> execute(UUID adminId, UserSearchCriteria criteria, Pageable pageable);
}