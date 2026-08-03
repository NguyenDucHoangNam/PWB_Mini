package com.pwb.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = "com.pwb.web")
@ConfigurationPropertiesScan(basePackages = "com.pwb.web")
public class WebAutoConfiguration {
}