package com.pwb.infra.config;

import com.pwb.infra.mail.renderer.EmailTemplateRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

@AutoConfiguration
@ConditionalOnClass(EmailTemplateRegistry.class)
public class MailAutoConfiguration {
}
