package com.pwb.audio.application.support;

import com.pwb.audio.domain.service.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleaner {

    private final StoragePort storagePort;

    public void deleteAfterCommit(String... storageKeys) {
        List<String> keys = Arrays.stream(storageKeys)
                .filter(key -> key != null && !key.isBlank())
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keys.forEach(this::deleteQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(StorageCleaner.this::deleteQuietly);
            }
        });
    }

    public void deleteNow(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        deleteQuietly(storageKey);
    }

    private void deleteQuietly(String storageKey) {
        try {
            storagePort.delete(storageKey);
            log.debug("Deleted audio object: storageKey={}", storageKey);
        } catch (Exception ex) {
            log.error("Orphaned audio object, manual cleanup required: storageKey={}", storageKey, ex);
        }
    }
}
