package com.pwb.backend.config;

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

    /**
     * Enable/disable user seeding on startup.
     */
    private boolean enabled = false;

    /**
     * List of users to seed.
     */
    private List<SeedUser> users = new ArrayList<>();

    @Getter
    @Setter
    public static class SeedUser {
        private String email;
        private String fullName;
        private String role;
        private String password;
    }
}
