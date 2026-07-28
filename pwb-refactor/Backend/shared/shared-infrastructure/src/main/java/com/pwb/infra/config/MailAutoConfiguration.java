package com.pwb.infra.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = {
        "com.pwb.infra.mail",
        "com.pwb.infra.mail.api",
        "com.pwb.infra.mail.consumer",
        "com.pwb.infra.mail.renderer"
})
public class MailAutoConfiguration {
}