package com.pwb.infra.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = {
        "com.pwb.infra.mail",
        "com.pwb.infra.redis",
        "com.pwb.infra.storage",
        "com.pwb.infra.kafka"
})
public class InfraAutoConfiguration {
}