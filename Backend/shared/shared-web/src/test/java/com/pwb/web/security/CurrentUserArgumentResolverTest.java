package com.pwb.web.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrentUserArgumentResolver — resolve @CurrentUser")
class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should_support_parameter_with_currentuser_annotation_and_authenticated_user_type")
    void should_support_parameter_with_currentuser_annotation_and_authenticated_user_type() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("authenticatedUserMethod", AuthenticatedUser.class);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    @DisplayName("should_support_parameter_with_currentuser_annotation_and_uuid_type")
    void should_support_parameter_with_currentuser_annotation_and_uuid_type() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("uuidMethod", UUID.class);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    @DisplayName("should_not_support_parameter_without_currentuser_annotation")
    void should_not_support_parameter_without_currentuser_annotation() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("nonAnnotatedMethod", AuthenticatedUser.class);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("should_not_support_parameter_with_unsupported_type")
    void should_not_support_parameter_with_unsupported_type() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("stringMethod", String.class);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("should_resolve_authenticated_user_when_authenticated")
    void should_resolve_authenticated_user_when_authenticated() throws NoSuchMethodException {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(UUID.randomUUID().toString())
                .email("a@b.com")
                .authorities(Set.of("USER"))
                .isOAuthUser(false)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        MethodParameter parameter = methodParameter("authenticatedUserMethod", AuthenticatedUser.class);
        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) resolved).getEmail()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("should_resolve_uuid_when_authenticated_user_with_uuid_type")
    void should_resolve_uuid_when_authenticated_user_with_uuid_type() throws NoSuchMethodException {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId.toString())
                .email("a@b.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        MethodParameter parameter = methodParameter("uuidMethod", UUID.class);
        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isEqualTo(userId);
    }

    @Test
    @DisplayName("should_return_null_when_not_authenticated")
    void should_return_null_when_not_authenticated() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("authenticatedUserMethod", AuthenticatedUser.class);

        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("should_return_null_when_principal_is_not_authenticated_user")
    void should_return_null_when_principal_is_not_authenticated_user() throws NoSuchMethodException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-string-principal", null, List.of()));

        MethodParameter parameter = methodParameter("authenticatedUserMethod", AuthenticatedUser.class);
        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("should_return_null_when_user_id_is_invalid_uuid_string")
    void should_return_null_when_user_id_is_invalid_uuid_string() throws NoSuchMethodException {
        AuthenticatedUser principal = AuthenticatedUser.builder().userId("not-a-uuid").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        MethodParameter parameter = methodParameter("uuidMethod", UUID.class);
        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("should_return_null_when_uuid_target_but_user_id_null")
    void should_return_null_when_uuid_target_but_user_id_null() throws NoSuchMethodException {
        AuthenticatedUser principal = AuthenticatedUser.builder().userId(null).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        MethodParameter parameter = methodParameter("uuidMethod", UUID.class);
        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isNull();
    }

    private static MethodParameter methodParameter(String methodName, Class<?> paramType) throws NoSuchMethodException {
        java.lang.reflect.Method method = CurrentUserSampleController.class.getMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }
}