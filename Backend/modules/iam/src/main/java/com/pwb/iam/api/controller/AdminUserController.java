package com.pwb.iam.api.controller;

import com.pwb.iam.api.dto.request.BanUserRequest;
import com.pwb.iam.api.dto.request.ChangeRoleRequest;
import com.pwb.iam.api.dto.request.DeleteUserRequest;
import com.pwb.iam.api.dto.response.AdminUserResponse;
import com.pwb.iam.api.dto.response.AdminUserStatsResponse;
import com.pwb.iam.application.command.AdminBanUserCommand;
import com.pwb.iam.application.command.AdminChangeRoleCommand;
import com.pwb.iam.application.command.AdminDeleteUserCommand;
import com.pwb.iam.application.command.AdminUnbanUserCommand;
import com.pwb.iam.application.dto.AdminUserStatsView;
import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.usecase.AdminBanUserUseCase;
import com.pwb.iam.application.usecase.AdminChangeRoleUseCase;
import com.pwb.iam.application.usecase.AdminDeleteUserUseCase;
import com.pwb.iam.application.usecase.AdminGetUserDetailUseCase;
import com.pwb.iam.application.usecase.AdminListUsersUseCase;
import com.pwb.iam.application.usecase.AdminUnbanUserUseCase;
import com.pwb.iam.application.usecase.AdminUserStatsUseCase;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.shared.dto.ApiResponse;
import com.pwb.shared.dto.PageResponse;
import com.pwb.web.dto.PageResponses;
import com.pwb.web.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminListUsersUseCase listUsers;
    private final AdminGetUserDetailUseCase getUserDetail;
    private final AdminChangeRoleUseCase changeRole;
    private final AdminBanUserUseCase banUser;
    private final AdminUnbanUserUseCase unbanUser;
    private final AdminDeleteUserUseCase deleteUser;
    private final AdminUserStatsUseCase userStats;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> list(
            @CurrentUser UUID adminId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) OAuthProvider provider,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UserSearchCriteria criteria = new UserSearchCriteria(keyword, status, role, provider);
        Page<AdminUserView> page = listUsers.execute(adminId, criteria, pageable);
        PageResponse<AdminUserResponse> body = PageResponses.from(page, AdminUserResponse::from);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getDetail(
            @CurrentUser UUID adminId,
            @PathVariable UUID userId
    ) {
        AdminUserView view = getUserDetail.execute(adminId, userId);
        return ResponseEntity.ok(ApiResponse.success(AdminUserResponse.from(view)));
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
            @CurrentUser UUID adminId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        RoleName newRole = RoleName.valueOf(request.role().toUpperCase());
        AdminChangeRoleCommand command = new AdminChangeRoleCommand(adminId, userId, newRole);
        AdminUserView view = changeRole.execute(command);
        return ResponseEntity.ok(ApiResponse.success(AdminUserResponse.from(view)));
    }

    @PostMapping("/{userId}/ban")
    public ResponseEntity<ApiResponse<AdminUserResponse>> ban(
            @CurrentUser UUID adminId,
            @PathVariable UUID userId,
            @Valid @RequestBody BanUserRequest request
    ) {
        AdminBanUserCommand command = new AdminBanUserCommand(adminId, userId, request.reason());
        AdminUserView view = banUser.execute(command);
        return ResponseEntity.ok(ApiResponse.success(AdminUserResponse.from(view)));
    }

    @PostMapping("/{userId}/unban")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unban(
            @CurrentUser UUID adminId,
            @PathVariable UUID userId
    ) {
        AdminUnbanUserCommand command = new AdminUnbanUserCommand(adminId, userId);
        AdminUserView view = unbanUser.execute(command);
        return ResponseEntity.ok(ApiResponse.success(AdminUserResponse.from(view)));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(
            @CurrentUser UUID adminId,
            @PathVariable UUID userId,
            @Valid @RequestBody(required = false) DeleteUserRequest request
    ) {
        String reason = request == null ? null : request.reason();
        AdminDeleteUserCommand command = new AdminDeleteUserCommand(adminId, userId, reason);
        deleteUser.execute(command);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminUserStatsResponse>> stats() {
        AdminUserStatsView view = userStats.execute();
        return ResponseEntity.ok(ApiResponse.success(AdminUserStatsResponse.from(view)));
    }
}
