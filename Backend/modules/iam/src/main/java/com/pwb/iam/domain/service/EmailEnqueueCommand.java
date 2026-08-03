package com.pwb.iam.domain.service;

import com.pwb.infra.mail.api.EmailTemplate;

import java.util.Map;
import java.util.UUID;

public record EmailEnqueueCommand(
        UUID userId,
        String email,
        EmailTemplate template,
        Map<String, String> variables,
        String locale
) {
}
