package com.pwb.notification.infrastructure.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private String host = "localhost";
    private int port = 1025;
    private String username;
    private String password;
    private String defaultFrom = "no-reply@pwb.local";
    private boolean authEnabled = false;
    private boolean starttlsEnabled = false;
}
