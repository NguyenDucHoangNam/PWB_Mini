package com.pwb.iam.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.iam.seeder")
public class SeederProperties {

    private boolean enabled = false;

    private List<SeedUser> users = new ArrayList<>();

    @Getter
    @Setter
    public static class SeedUser {
        private String email;
        private String password;
        private String fullName;
        private String role;
    }
}
