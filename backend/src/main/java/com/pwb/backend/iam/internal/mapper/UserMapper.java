package com.pwb.backend.iam.internal.mapper;

import com.pwb.backend.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.iam.api.dto.response.UserProfileResponse;
import com.pwb.backend.iam.internal.model.User;
import com.pwb.backend.shared.dto.UserInfoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "oauthProvider", ignore = true)
  @Mapping(target = "oauthId", ignore = true)
  @Mapping(target = "avatarUrl", ignore = true)
  @Mapping(target = "phone", ignore = true)
  @Mapping(target = "deletionRequestedAt", ignore = true)
  @Mapping(target = "password", ignore = true)
  User toEntity(RegisterRequest request);

  RegisterResponse toRegisterResponse(User user);

  default UserInfoResponse toUserInfo(User user) {
    if (user == null) return null;
    return new UserInfoResponse(
        user.getUsername(),
        user.getEmail(),
        user.getFullName(),
        user.getRole() == null ? null : user.getRole().getName(),
        user.getStatus() == null ? null : user.getStatus().name(),
        user.getOauthProvider() == null ? "LOCAL" : user.getOauthProvider().name()
    );
  }

  default UserProfileResponse toUserProfileResponse(User user) {
    if (user == null) return null;
    return new UserProfileResponse(
        user.getUsername(),
        user.getEmail(),
        user.getFullName(),
        user.getRole() == null ? null : user.getRole().getName(),
        user.getStatus() == null ? null : user.getStatus().name(),
        user.getAvatarUrl(),
        user.getPhone(),
        user.getOauthProvider() == null ? "LOCAL" : user.getOauthProvider().name(),
        user.getDeletionRequestedAt()
    );
  }
}
