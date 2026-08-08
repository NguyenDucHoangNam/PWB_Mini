package com.pwb.iam.application.service;

import com.pwb.iam.domain.exception.IamErrorCode;
import com.pwb.iam.domain.model.PasswordHistory;
import com.pwb.iam.domain.repository.PasswordHistoryRepository;
import com.pwb.iam.domain.service.PasswordHasher;
import com.pwb.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordHistoryGuard {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordHasher passwordHasher;

    public void assertNotReused(UUID userId, String newRawPassword, String currentHash) {
        if (currentHash != null && passwordHasher.matches(newRawPassword, currentHash)) {
            throw new BusinessException(IamErrorCode.AUTH_PASSWORD_REUSED);
        }

        List<PasswordHistory> history = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PasswordHistory.MAX_HISTORY_SIZE);
        for (PasswordHistory entry : history) {
            if (passwordHasher.matches(newRawPassword, entry.getPasswordHash())) {
                throw new BusinessException(IamErrorCode.AUTH_PASSWORD_RECENTLY_USED);
            }
        }
    }

    public void record(UUID userId, String replacedHash) {
        if (replacedHash == null || replacedHash.isBlank()) {
            return;
        }
        passwordHistoryRepository.save(PasswordHistory.create(userId, replacedHash));

        long count = passwordHistoryRepository.countByUserId(userId);
        if (count > PasswordHistory.MAX_HISTORY_SIZE) {
            passwordHistoryRepository.deleteOldestByUserId(
                    userId, (int) (count - PasswordHistory.MAX_HISTORY_SIZE));
        }
    }
}
