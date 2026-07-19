package com.pwb.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.util.UUID;

@Slf4j
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthenticatedUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticated) {
            return authenticated;
        }
        return adaptPrincipal(principal);
    }

    private AuthenticatedUser adaptPrincipal(Object principal) {
        try {
            Method getId = findMethod(principal.getClass(), "getId");
            UUID id = getId == null ? null : (UUID) getId.invoke(principal);

            Method getUsername = findMethod(principal.getClass(), "getUsername");
            String username = getUsername == null ? null : (String) getUsername.invoke(principal);

            String role = null;
            Method getAuthorities = findMethod(principal.getClass(), "getAuthorities");
            if (getAuthorities != null) {
                Object authorities = getAuthorities.invoke(principal);
                if (authorities instanceof Iterable<?> iterable && iterable.iterator().hasNext()) {
                    role = String.valueOf(iterable.iterator().next());
                }
            }

            return new DefaultAuthenticatedUser(id, username, role);
        } catch (ReflectiveOperationException ex) {
            log.debug("Cannot adapt principal {} to AuthenticatedUser: {}", principal.getClass().getName(), ex.getMessage());
            return null;
        }
    }

    private Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    public record DefaultAuthenticatedUser(
            UUID id,
            String username,
            String role) implements AuthenticatedUser {

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getRole() {
            return role;
        }
    }
}