package com.pwb.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CurrentUserAgentArgumentResolver — resolve @CurrentUserAgent")
class CurrentUserAgentArgumentResolverTest {

    private static final int MAX_LENGTH = 512;

    private final CurrentUserAgentArgumentResolver resolver = new CurrentUserAgentArgumentResolver();

    @Test
    @DisplayName("should_support_parameter_with_currentuseragent_annotation_and_string_type")
    void should_support_parameter_with_currentuseragent_annotation_and_string_type() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("annotatedStringMethod", String.class);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    @DisplayName("should_not_support_parameter_without_annotation")
    void should_not_support_parameter_without_annotation() throws NoSuchMethodException {
        MethodParameter parameter = methodParameter("nonAnnotatedMethod", String.class);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("should_resolve_user_agent_header_when_present")
    void should_resolve_user_agent_header_when_present() throws NoSuchMethodException {
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat(resolved).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("should_return_unknown_when_header_missing")
    void should_return_unknown_when_header_missing() throws NoSuchMethodException {
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getHeader("User-Agent")).thenReturn(null);

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat(resolved).isEqualTo("unknown");
    }

    @Test
    @DisplayName("should_return_unknown_when_header_blank")
    void should_return_unknown_when_header_blank() throws NoSuchMethodException {
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getHeader("User-Agent")).thenReturn("   ");

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat(resolved).isEqualTo("unknown");
    }

    @Test
    @DisplayName("should_truncate_user_agent_when_exceeds_max_length")
    void should_truncate_user_agent_when_exceeds_max_length() throws NoSuchMethodException {
        StringBuilder longUa = new StringBuilder();
        for (int i = 0; i < MAX_LENGTH + 50; i++) {
            longUa.append("x");
        }
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getHeader("User-Agent")).thenReturn(longUa.toString());

        Object resolved = resolver.resolveArgument(methodParameter("annotatedStringMethod", String.class),
                mock(ModelAndViewContainer.class), webRequest, mock(WebDataBinderFactory.class));

        assertThat((String) resolved).hasSize(MAX_LENGTH);
    }

    private static MethodParameter methodParameter(String methodName, Class<?> paramType) throws NoSuchMethodException {
        java.lang.reflect.Method method = CurrentUserAgentSampleController.class.getMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }
}