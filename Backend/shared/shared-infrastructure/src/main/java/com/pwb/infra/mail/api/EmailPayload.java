package com.pwb.infra.mail.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

public record EmailPayload(
        String template,
        String toEmail,
        UUID userId,
        Map<String, String> variables,
        String locale,
        String subject,
        String htmlBody,
        String textBody,
        String from
) {

    @JsonCreator
    public EmailPayload(
            @JsonProperty("template") String template,
            @JsonProperty("toEmail") String toEmail,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("variables") Map<String, String> variables,
            @JsonProperty("locale") String locale,
            @JsonProperty("subject") String subject,
            @JsonProperty("htmlBody") String htmlBody,
            @JsonProperty("textBody") String textBody,
            @JsonProperty("from") String from
    ) {
        this.template = template;
        this.toEmail = toEmail;
        this.userId = userId;
        this.variables = variables == null ? Map.of() : variables;
        this.locale = locale;
        this.subject = subject;
        this.htmlBody = htmlBody;
        this.textBody = textBody;
        this.from = from;
    }
}