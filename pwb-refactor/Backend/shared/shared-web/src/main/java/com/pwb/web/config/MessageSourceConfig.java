package com.pwb.web.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(
            "classpath:messages/messages",
            "classpath:com/pwb/iam/messages/messages"
        );
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        source.setDefaultLocale(Locale.ENGLISH);
        return source;
    }

    @Bean
    public BeanPostProcessor localeResolverCustomizer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof AcceptHeaderLocaleResolver resolver) {
                    resolver.setDefaultLocale(Locale.ENGLISH);
                    resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("vi")));
                }
                return bean;
            }
        };
    }
}
