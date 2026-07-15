package com.pwb.backend.mapper;

import com.pwb.backend.dto.response.AuthResponse;
import com.pwb.backend.dto.response.AuthResponse.NextStep;
import com.pwb.backend.entity.rdbms.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface AuthMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.status", target = "status")
    @Mapping(target = "role", expression = "java(user.getRole() == null ? null : user.getRole().getName())")
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "expiresIn", ignore = true)
    @Mapping(target = "nextStep", ignore = true)
    void fillStaticFields(User user, @MappingTarget AuthResponse response);

    default AuthResponse toAuthResponse(User user, NextStep nextStep) {
        AuthResponse response = AuthResponse.builder().build();
        fillStaticFields(user, response);
        response.setNextStep(nextStep);
        return response;
    }
}