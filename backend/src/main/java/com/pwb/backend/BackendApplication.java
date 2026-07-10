package com.pwb.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

@Modulith(sharedModules = "shared")
@EnableScheduling
@EnableAsync
// M1: the following @Enable* annotations used to live in the shared module's
// individual @Configuration classes, which made the shared module impossible
// to opt out of when slicing it for tests or splitting it out as a microservice.
// They now live at the application bootstrap so module-local configurations
// can be reused in isolation.
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
@EnableWebSocketMessageBroker
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}