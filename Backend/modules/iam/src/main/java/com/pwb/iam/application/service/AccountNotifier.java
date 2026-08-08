package com.pwb.iam.application.service;

import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.infra.mail.api.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountNotifier {

    private static final String FALLBACK_DISPLAY_NAME = "bạn";

    private final EmailDeliveryPort emailDeliveryPort;

    public void passwordChanged(User user, String locale) {
        enqueue(user, EmailTemplate.PASSWORD_CHANGED, Map.of(), locale);
    }

    public void googleAccountLinked(User user, String locale) {
        enqueue(user, EmailTemplate.ACCOUNT_LINKED_GOOGLE,
                Map.of("displayName", displayNameOf(user)), locale);
    }

    private void enqueue(User user, EmailTemplate template, Map<String, String> variables, String locale) {
        if (user.getEmail() == null) {
            log.warn("Skipping {} notification, user has no email: userId={}", template, user.getUserId());
            return;
        }
        emailDeliveryPort.enqueue(new EmailEnqueueCommand(
                user.getUserId(), user.getEmail().value(), template, variables, locale));
    }

    private String displayNameOf(User user) {
        String fullName = user.getFullName();
        return fullName == null || fullName.isBlank() ? FALLBACK_DISPLAY_NAME : fullName;
    }
}
