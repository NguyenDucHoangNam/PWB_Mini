package com.pwb.web.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
public class MessageSourceConfig {

    private static final String SHARED_WEB_BUNDLE = "classpath:messages/messages";
    private static final String IAM_BUNDLE = "classpath:iam/messages";
    private static final String AUDIO_BUNDLE = "classpath:audio/messages";
    private static final String SHARED_INFRA_BUNDLE = "classpath:infra/messages";

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(
                SHARED_WEB_BUNDLE,
                IAM_BUNDLE,
                AUDIO_BUNDLE,
                SHARED_INFRA_BUNDLE
        );
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        source.setDefaultLocale(Locale.forLanguageTag("vi"));
        return source;
    }
}
