package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.exception.BusinessException;
import com.pwb.backend.common.exception.GlobalExceptionHandler;
import com.pwb.backend.modules.iam.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.modules.iam.job.AccountAnonymizationJob;
import com.pwb.backend.modules.iam.service.AnonymizationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminJobControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountAnonymizationJob anonymizationJob;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private AdminJobController adminJobController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminJobController)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
    }

    @Test
    void triggerAnonymization_success() throws Exception {
        AnonymizationReport report = new AnonymizationReport(5, 100L, "COMPLETED");
        TriggerAnonymizationResponse response = new TriggerAnonymizationResponse(5, 100L, "COMPLETED");

        when(anonymizationJob.runManual(100)).thenReturn(report);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Anonymization triggered");

        mockMvc.perform(post("/api/v1/admin/jobs/trigger-anonymization"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedUsersCount").value(5))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void triggerAnonymization_empty() throws Exception {
        AnonymizationReport report = new AnonymizationReport(0, 50L, "EMPTY");
        TriggerAnonymizationResponse response = new TriggerAnonymizationResponse(0, 50L, "EMPTY");

        when(anonymizationJob.runManual(100)).thenReturn(report);
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Anonymization triggered");

        mockMvc.perform(post("/api/v1/admin/jobs/trigger-anonymization"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedUsersCount").value(0))
                .andExpect(jsonPath("$.data.status").value("EMPTY"));
    }

    @Test
    void triggerAnonymization_serviceThrows() throws Exception {
        when(anonymizationJob.runManual(100)).thenThrow(new RuntimeException("Database error"));
        when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("Internal error");

        mockMvc.perform(post("/api/v1/admin/jobs/trigger-anonymization"))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}
