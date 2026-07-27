package com.pwb.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication(scanBasePackages = "com.pwb")
@EntityScan(basePackages = "com.pwb")
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
