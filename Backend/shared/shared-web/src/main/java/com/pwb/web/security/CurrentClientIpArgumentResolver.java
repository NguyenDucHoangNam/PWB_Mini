package com.pwb.web.security;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentClientIpArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String CLIENT_IP_ATTRIBUTE = "clientIp";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentClientIp.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object value = webRequest.getAttribute(CLIENT_IP_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        return value instanceof String s ? s : "unknown";
    }
}