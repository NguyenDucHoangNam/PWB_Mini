package com.pwb.iam.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@Configuration
@ComponentScan(basePackages = "com.pwb.iam")
@EnableJpaRepositories(basePackages = "com.pwb.iam")
public class IamAutoConfiguration {
}