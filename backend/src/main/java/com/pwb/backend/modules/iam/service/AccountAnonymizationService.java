package com.pwb.backend.modules.iam.service;

public interface AccountAnonymizationService {

    AnonymizationReport runOnce(int batchSize);
}