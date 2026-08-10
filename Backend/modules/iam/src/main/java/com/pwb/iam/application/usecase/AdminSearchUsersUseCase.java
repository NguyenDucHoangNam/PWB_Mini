package com.pwb.iam.application.usecase;

import com.pwb.iam.application.dto.AdminUserSuggestionView;
import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminSearchUsersUseCase {

    /** Admin search. Runs the same specification query the plain listing uses, with the keyword added. */
    Page<AdminUserView> search(UUID adminId, UserSearchCriteria criteria, Pageable pageable);

    List<AdminUserSuggestionView> suggest(UUID adminId, UserSearchCriteria criteria, int limit);
}
