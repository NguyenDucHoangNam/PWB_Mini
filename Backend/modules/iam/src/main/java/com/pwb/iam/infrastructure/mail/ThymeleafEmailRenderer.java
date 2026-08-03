package com.pwb.iam.infrastructure.mail;

import com.pwb.infra.mail.api.EmailTemplate;
import com.pwb.infra.mail.consumer.MailTemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThymeleafEmailRenderer {

    private static final String HTML_TEMPLATE_PREFIX = "email/";
    private static final String CONTENT_FRAGMENT_SELECTOR = "content";
    private static final String DEFAULT_LOCALE = "vi";

    private final SpringTemplateEngine emailTemplateEngine;
    private final MessageSource messageSource;

    public String renderHtml(EmailTemplate template, Map<String, String> variables, String localeTag) {
        Locale locale = resolveLocale(localeTag);
        Context context = buildContext(variables, locale, resolveSubject(template, localeTag));
        String templateName = HTML_TEMPLATE_PREFIX + template.name().toLowerCase().replace('_', '-');
        try {
            return emailTemplateEngine.process(templateName, Set.of(CONTENT_FRAGMENT_SELECTOR), context);
        } catch (TemplateInputException ex) {
            throw new MailTemplateException(
                    "HTML template input error for " + template.name() + ": " + ex.getMessage(), ex);
        } catch (TemplateEngineException ex) {
            throw new MailTemplateException(
                    "HTML template processing failed for " + template.name() + " (locale=" + localeTag + "): "
                            + ex.getMessage(), ex);
        }
    }

    public String renderText(EmailTemplate template, Map<String, String> variables, String localeTag) {
        Locale locale = resolveLocale(localeTag);
        Context context = buildContext(variables, locale, resolveSubject(template, localeTag));
        String templateName = HTML_TEMPLATE_PREFIX + template.name().toLowerCase().replace('_', '-');
        try {
            return emailTemplateEngine.process(templateName, context);
        } catch (TemplateInputException ex) {
            throw new MailTemplateException(
                    "Text template input error for " + template.name() + ": " + ex.getMessage(), ex);
        } catch (TemplateEngineException ex) {
            log.warn("Text template not found for {}, returning empty text body: {}",
                    template.name(), ex.getMessage());
            return "";
        }
    }

    public String resolveSubject(EmailTemplate template, String localeTag) {
        Locale locale = resolveLocale(localeTag);
        return messageSource.getMessage(
                "email." + template.name().toLowerCase() + ".subject",
                null,
                locale);
    }

    private Context buildContext(Map<String, String> variables, Locale locale, String subject) {
        Context context = new Context(locale);
        context.setVariable("pageTitle", subject);
        if (variables != null) {
            variables.forEach(context::setVariable);
        }
        return context;
    }

    private Locale resolveLocale(String localeTag) {
        if (localeTag == null || localeTag.isBlank()) {
            return Locale.forLanguageTag(DEFAULT_LOCALE);
        }
        return Locale.forLanguageTag(localeTag);
    }
}