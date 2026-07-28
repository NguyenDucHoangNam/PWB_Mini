package com.pwb.infra.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan(basePackages = "com.pwb.infra")
public class InfraAutoConfiguration {
}