package com.pwb.backend.modules.iam.controller;

import com.pwb.backend.common.dto.ApiResponse;
import com.pwb.backend.modules.iam.dto.response.TriggerAnonymizationResponse;
import com.pwb.backend.modules.iam.job.AccountAnonymizationJob;
import com.pwb.backend.modules.iam.service.AnonymizationReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
@Slf4j
public class AdminJobController {

    private static final String MSG_ANONYMIZATION_TRIGGERED = "ANONYMIZATION_TRIGGERED";
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final AccountAnonymizationJob anonymizationJob;
    private final MessageSource messageSource;

    @PostMapping("/trigger-anonymization")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TriggerAnonymizationResponse>> triggerAnonymization() {
        log.info("ADMIN_TRIGGER_ANONYMIZATION_REQUEST_RECEIVED");
        AnonymizationReport report = anonymizationJob.runManual(DEFAULT_BATCH_SIZE);
        TriggerAnonymizationResponse data = new TriggerAnonymizationResponse(
                report.processedCount(),
                report.durationMs(),
                report.status());
        log.info("ADMIN_TRIGGER_ANONYMIZATION_RESULT processedUsers={} durationMs={} status={}",
                data.processedUsersCount(), data.executionTimeMs(), data.status());
        return ResponseEntity.ok(ApiResponse.success(message(MSG_ANONYMIZATION_TRIGGERED), data));
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}