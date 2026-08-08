package com.pwb.iam.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "pwb.iam.seeder")
public class SeederProperties {

    private boolean enabled = false;
    private List<SeedUser> users = new ArrayList<>();

    @Data
    public static class SeedUser {
        private String email;
        private String password;
        private String fullName;
        private String role;
        private String status;
    }
}
