package com.pwb.iam.infrastructure.security;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.web.security.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        ErrorResponseWriter.write(response, IamErrorCode.AUTH_TOKEN_MISSING);
    }
}
