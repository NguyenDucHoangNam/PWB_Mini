package com.pwb.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CurrentClientIpArgumentResolver — resolve @CurrentClientIp")
class CurrentClientIpArgumentResolverTest {

    private final CurrentClientIpArgumentResolver resolver = new CurrentClientIpArgumentResolver();

    @Test
    @DisplayName("should_support_parameter_with_currentclientip_annotation_and_string_type")
    void should_support_parameter_with_currentclientip_annotation_and_string_type() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("annotatedStringMethod", String.class);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    @DisplayName("should_not_support_parameter_without_currentclientip_annotation")
    void should_not_support_parameter_without_currentclientip_annotation() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("nonAnnotatedMethod", String.class);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("should_not_support_parameter_with_non_string_type")
    void should_not_support_parameter_with_non_string_type() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("annotatedUuidMethod", UUID.class);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("should_resolve_client_ip_from_request_attribute")
    void should_resolve_client_ip_from_request_attribute() throws NoSuchMethodException {
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getAttribute("clientIp", NativeWebRequest.SCOPE_REQUEST)).thenReturn("203.0.113.7");

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat(resolved).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("should_return_unknown_when_attribute_missing")
    void should_return_unknown_when_attribute_missing() throws NoSuchMethodException {
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getAttribute("clientIp", NativeWebRequest.SCOPE_REQUEST)).thenReturn(null);

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat(resolved).isEqualTo("unknown");
    }

    @Test
    @DisplayName("should_return_unknown_when_attribute_is_non_string")
    void should_return_unknown_when_attribute_is_non_string() throws NoSuchMethodException {
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getAttribute("clientIp", NativeWebRequest.SCOPE_REQUEST)).thenReturn(Integer.valueOf(42));

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat(resolved).isEqualTo("unknown");
    }

    @Test
    @DisplayName("should_keep_attribute_value_when_string")
    void should_keep_attribute_value_when_string() throws NoSuchMethodException {
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getAttribute("clientIp", NativeWebRequest.SCOPE_REQUEST)).thenReturn("");

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat(resolved).isEqualTo("");
    }

    private static MethodParameter methodParameter(String methodName, Class<?> paramType) throws NoSuchMethodException {
        java.lang.reflect.Method method = CurrentClientIpSampleController.class.getMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }
}