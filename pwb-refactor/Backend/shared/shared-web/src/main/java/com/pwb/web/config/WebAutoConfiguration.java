package com.pwb.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan(basePackages = "com.pwb.web")
public class WebAutoConfiguration {
}