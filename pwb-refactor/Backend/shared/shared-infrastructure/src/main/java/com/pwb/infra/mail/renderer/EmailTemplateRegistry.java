package com.pwb.infra.mail.renderer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EmailTemplateRegistry {

    private final List<EmailTemplateRenderer> renderers;

    private Map<String, EmailTemplateRenderer> byName;

    private Map<String, EmailTemplateRenderer> renderersByName() {
        if (byName == null) {
            byName = renderers.stream()
                    .collect(Collectors.toMap(EmailTemplateRenderer::name, Function.identity()));
        }
        return byName;
    }

    public Optional<EmailTemplateRenderer> find(String templateName) {
        if (templateName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(renderersByName().get(templateName));
    }

    public String render(String templateName, Map<String, String> variables, String locale) {
        return find(templateName)
                .map(r -> r.render(variables, locale))
                .orElse("[no-template:" + templateName + "]");
    }

    public String subject(String templateName, Map<String, String> variables, String locale) {
        return find(templateName)
                .map(r -> r.subject(variables, locale))
                .orElse("[no-template:" + templateName + "]");
    }
}