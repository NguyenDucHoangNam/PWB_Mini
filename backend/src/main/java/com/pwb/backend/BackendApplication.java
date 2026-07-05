package com.pwb.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulith(sharedModules = "shared")
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
