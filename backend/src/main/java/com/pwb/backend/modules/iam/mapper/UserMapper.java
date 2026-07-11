package com.pwb.backend.modules.iam.mapper;

import com.pwb.backend.modules.iam.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.dto.response.UserInfo;
import com.pwb.backend.modules.iam.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "oauthProvider", ignore = true)
    @Mapping(target = "oauthId", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "emailVerifiedAt", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    User registerRequestToUser(RegisterRequest req);

    default RegisterResponse userToRegisterResponse(User user) {
        return RegisterResponse.verificationSent(user.getEmail(), null);
    }

    default UserInfo toUserInfo(User user) {
        if (user == null) {
            return null;
        }
        String roleCode = user.getRole() != null ? user.getRole().getCode() : "USER";
        return new UserInfo(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roleCode,
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getAvatarUrl());
    }
}
