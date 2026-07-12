package com.pwb.backend.modules.iam.job;

import com.pwb.backend.modules.iam.service.AnonymizationReport;
import com.pwb.backend.modules.iam.service.AccountAnonymizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountAnonymizationJobTest {

    @Mock
    private AccountAnonymizationService anonymizationService;

    private AccountAnonymizationJob anonymizationJob;

    @BeforeEach
    void setUp() {
        anonymizationJob = new AccountAnonymizationJob(anonymizationService);
    }

    @Test
    void runManual_callsRunOnce() {
        AnonymizationReport report = new AnonymizationReport(5, 100L, "COMPLETED");
        when(anonymizationService.runOnce(50)).thenReturn(report);

        AnonymizationReport result = anonymizationJob.runManual(50);

        assertNotNull(result);
        assertEquals(5, result.processedCount());
        verify(anonymizationService).runOnce(50);
    }

    @Test
    void runManual_withNegativeBatchSize_usesMinimum() {
        AnonymizationReport report = new AnonymizationReport(0, 10L, "EMPTY");
        when(anonymizationService.runOnce(1)).thenReturn(report);

        AnonymizationReport result = anonymizationJob.runManual(-10);

        assertNotNull(result);
        verify(anonymizationService).runOnce(1);
    }

    @Test
    void runManual_withZeroBatchSize_usesMinimum() {
        AnonymizationReport report = new AnonymizationReport(0, 10L, "EMPTY");
        when(anonymizationService.runOnce(1)).thenReturn(report);

        AnonymizationReport result = anonymizationJob.runManual(0);

        assertNotNull(result);
        verify(anonymizationService).runOnce(1);
    }

    @Test
    void runManual_returnsCorrectReport() {
        AnonymizationReport report = new AnonymizationReport(3, 200L, "COMPLETED");
        when(anonymizationService.runOnce(100)).thenReturn(report);

        AnonymizationReport result = anonymizationJob.runManual(100);

        assertEquals(3, result.processedCount());
        assertEquals("COMPLETED", result.status());
        assertEquals(200L, result.durationMs());
    }
}
