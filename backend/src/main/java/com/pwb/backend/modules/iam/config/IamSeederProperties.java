package com.pwb.backend.modules.iam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.iam.seeder")
@Getter
@Setter
public class IamSeederProperties {

    private boolean enabled = false;
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