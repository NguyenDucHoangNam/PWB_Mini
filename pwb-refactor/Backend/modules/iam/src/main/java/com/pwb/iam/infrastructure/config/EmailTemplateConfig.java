package com.pwb.iam.infrastructure.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@Configuration
public class EmailTemplateConfig {

    private static final String TEMPLATE_PREFIX = "templates/";
    private static final String TEMPLATE_ENCODING = "UTF-8";

    @Bean
    public SpringTemplateEngine emailTemplateEngine(MessageSource messageSource) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(emailHtmlTemplateResolver());
        engine.setMessageSource(messageSource);
        engine.setEnableSpringELCompiler(true);
        return engine;
    }

    @Bean
    public ITemplateResolver emailHtmlTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATE_PREFIX);
        resolver.setSuffix("");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(TEMPLATE_ENCODING);
        resolver.setCacheable(true);
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        return resolver;
    }

    @Bean
    public ITemplateResolver emailTextTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATE_PREFIX);
        resolver.setSuffix("");
        resolver.setTemplateMode("TEXT");
        resolver.setCharacterEncoding(TEMPLATE_ENCODING);
        resolver.setCacheable(true);
        resolver.setCheckExistence(true);
        resolver.setOrder(2);
        return resolver;
    }
}