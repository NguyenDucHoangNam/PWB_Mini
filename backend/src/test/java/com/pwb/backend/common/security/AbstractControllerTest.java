package com.pwb.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pwb.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
public abstract class AbstractControllerTest {

    protected MockMvc mockMvc;
    protected ObjectMapper objectMapper;

    @Mock
    protected MessageSource messageSource;

    @BeforeEach
    protected void baseSetUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(getControllerUnderTest())
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
        setupMessageSourceMock();
    }

    protected void setupMessageSourceMock() {
        lenient().when(messageSource.getMessage(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
    }

    protected String message(String key) {
        return key;
    }

    protected abstract Object getControllerUnderTest();
}
