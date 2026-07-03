package com.pwb.backend.modules.iam.internal.mapper;

import com.pwb.backend.modules.iam.api.dto.request.RegisterRequest;
import com.pwb.backend.modules.iam.api.dto.response.RegisterResponse;
import com.pwb.backend.modules.iam.api.dto.response.VerifyOtpResponse;
import com.pwb.backend.modules.iam.internal.model.User;
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
  @Mapping(target = "password", ignore = true)
  User toEntity(RegisterRequest request);

  RegisterResponse toRegisterResponse(User user);

  VerifyOtpResponse.UserInfo toUserInfo(User user);
}
