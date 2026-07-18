package com.pwb.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = "com.pwb")
@EntityScan(basePackages = {"com.pwb.iam.infrastructure.persistence.entity", "com.pwb.outbox.infrastructure.persistence.entity"})
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
