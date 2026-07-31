package com.pwb.infra.mail.config;

import com.pwb.infra.mail.properties.MailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    @ConditionalOnMissingBean
    public JavaMailSender javaMailSender(
            @org.springframework.beans.factory.annotation.Value("${spring.mail.host:localhost}") String host,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.port:1025}") int port,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") String username,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.password:}") String password,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.properties.mail.smtp.auth:true}") boolean auth,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean starttlsEnable,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.properties.mail.smtp.starttls.required:true}") boolean starttlsRequired) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttlsEnable));
        props.put("mail.smtp.starttls.required", String.valueOf(starttlsRequired));
        return sender;
    }
}
