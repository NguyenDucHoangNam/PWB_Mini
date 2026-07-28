package com.pwb.infra.mail.config;

import com.pwb.infra.mail.properties.MailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(
            @org.springframework.beans.factory.annotation.Value("${spring.mail.host:localhost}") String host,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.port:1025}") int port,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") String username,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.password:}") String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.getJavaMailProperties().put("mail.transport.protocol", "smtp");
        return sender;
    }
}
