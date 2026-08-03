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

import java.time.Year;
import java.util.Locale;
import java.util.Map;

/**
 * Renders transactional mail as {@code _layout.html} wrapping a per-template {@code content}
 * fragment.
 * <p>
 * All wording comes from the message bundle, keyed off the template name, so use cases only pass
 * domain data ({@code code}, {@code resetLink}, {@code displayName}) and never copy. Previously the
 * child fragment was processed on its own: the layout — and with it the header, logo and footer —
 * was never applied, and the body paragraphs resolved to null because nothing supplied
 * {@code bodyText}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThymeleafEmailRenderer {

    private static final String LAYOUT_TEMPLATE = "email/_layout";
    private static final String TEMPLATE_PREFIX = "email/";
    private static final String DEFAULT_LOCALE = "vi";

    private final SpringTemplateEngine emailTemplateEngine;
    private final MessageSource messageSource;

    public String renderHtml(EmailTemplate template, Map<String, String> variables, String localeTag) {
        Locale locale = resolveLocale(localeTag);
        Context context = buildContext(template, variables, locale);
        try {
            return emailTemplateEngine.process(LAYOUT_TEMPLATE, context);
        } catch (TemplateInputException ex) {
            throw new MailTemplateException(
                    "HTML template input error for " + template.name() + ": " + ex.getMessage(), ex);
        } catch (TemplateEngineException ex) {
            throw new MailTemplateException(
                    "HTML template processing failed for " + template.name()
                            + " (locale=" + localeTag + "): " + ex.getMessage(), ex);
        }
    }

    /**
     * Renders the plain-text alternative, or returns empty when the template has no {@code .txt}
     * variant.
     * <p>
     * The existence check is a correctness guard, not an optimisation: both resolvers sit on the
     * same engine with the HTML one ordered first, so processing a name without a {@code .txt}
     * file silently falls through to the {@code .html} file. That put a full HTML document into
     * the {@code text/plain} part and rendered every template twice.
     */
    public String renderText(EmailTemplate template, Map<String, String> variables, String localeTag) {
        String templateName = templateNameFor(template);
        if (!textTemplateExists(templateName)) {
            log.debug("No text template for {}, sending HTML-only message", template.name());
            return "";
        }
        try {
            return emailTemplateEngine.process(templateName, buildContext(template, variables, resolveLocale(localeTag)));
        } catch (TemplateEngineException ex) {
            log.warn("Text template failed for {}, sending HTML-only message: {}",
                    template.name(), ex.getMessage());
            return "";
        }
    }

    /**
     * Falls back to the template name rather than throwing: a missing subject key would otherwise
     * propagate out of the enqueue call and roll back the caller's transaction, failing e.g. a
     * whole registration over a translation gap.
     */
    public String resolveSubject(EmailTemplate template, String localeTag) {
        return text(template, "subject", template.name(), resolveLocale(localeTag));
    }

    private Context buildContext(EmailTemplate template, Map<String, String> variables, Locale locale) {
        String subject = text(template, "subject", template.name(), locale);

        Context context = new Context(locale);
        // Consumed by _layout.html.
        context.setVariable("childTemplate", templateNameFor(template));
        context.setVariable("title", text(template, "headline", subject, locale));
        context.setVariable("footerText", messageSource.getMessage("email.footer.tagline", null, "", locale));
        context.setVariable("year", String.valueOf(Year.now().getValue()));
        context.setVariable("pageTitle", subject);

        // Consumed by the content fragments. `body` is the current key; `intro` is the older name
        // still used by some bundles, so both are accepted before giving up.
        String body = text(template, "body", null, locale);
        if (body == null) {
            body = text(template, "intro", "", locale);
        }
        context.setVariable("bodyText", body);
        context.setVariable("farewellText", text(template, "ignore_notice", "", locale));

        // Presentation slots. Every visible sentence in a template resolves through one of these,
        // so no template holds literal prose: a hardcoded English line would otherwise reach a
        // Vietnamese reader untranslated, which is how the mails previously ended up bilingual.
        // Each defaults to empty, so a template that does not use a slot costs nothing and a
        // missing translation degrades to a blank line rather than an exception.
        context.setVariable("eyebrow", text(template, "eyebrow", "", locale));
        context.setVariable("ctaText", text(template, "cta", "", locale));
        context.setVariable("copyLinkHint", text(template, "copy_link_hint", "", locale));
        context.setVariable("cardTitle", text(template, "card_title", "", locale));
        context.setVariable("cardBody", text(template, "card_body", "", locale));
        context.setVariable("warningText", text(template, "warning", "", locale));
        context.setVariable("supportText", text(template, "support", "", locale));
        context.setVariable("greeting", parameterised(template, "greeting", displayNameOf(variables), locale));
        context.setVariable("ttlNotice", parameterised(template, "ttl_notice", ttlMinutesOf(variables), locale));

        if (variables != null) {
            // Domain values last: a use case passing `displayName` or `code` must win over any
            // same-named bundle entry.
            variables.forEach(context::setVariable);
        }
        return context;
    }

    /**
     * Resolves a message that takes a single {@code {0}} placeholder, or empty when the caller
     * supplied no value for it — a sentence like "Valid for {0} minutes" is meaningless without
     * the number, so it is omitted rather than rendered with a hole in it.
     */
    private String parameterised(EmailTemplate template, String suffix, String argument, Locale locale) {
        if (argument == null || argument.isBlank()) {
            return "";
        }
        return messageSource.getMessage(keyFor(template, suffix), new Object[]{argument}, "", locale);
    }

    private String text(EmailTemplate template, String suffix, String fallback, Locale locale) {
        return messageSource.getMessage(keyFor(template, suffix), null, fallback, locale);
    }

    private String keyFor(EmailTemplate template, String suffix) {
        return "email." + template.name().toLowerCase(Locale.ROOT) + "." + suffix;
    }

    private String displayNameOf(Map<String, String> variables) {
        return variables == null ? null : variables.get("displayName");
    }

    private String ttlMinutesOf(Map<String, String> variables) {
        return variables == null ? null : variables.get("ttlMinutes");
    }

    private boolean textTemplateExists(String templateName) {
        return getClass().getClassLoader().getResource("templates/" + templateName + ".txt") != null;
    }

    /**
     * {@code OTP_REGISTER} maps to {@code email/otp-register}. Template files must follow this
     * convention exactly — a mismatch surfaces only at send time, as an unresolved template.
     */
    private String templateNameFor(EmailTemplate template) {
        return TEMPLATE_PREFIX + template.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private Locale resolveLocale(String localeTag) {
        if (localeTag == null || localeTag.isBlank()) {
            return Locale.forLanguageTag(DEFAULT_LOCALE);
        }
        return Locale.forLanguageTag(localeTag);
    }
}
