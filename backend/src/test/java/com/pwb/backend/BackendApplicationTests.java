package com.pwb.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Full context load is blocked by Redisson 3.27.x incompatibility with Spring Boot 4.x (RedisProperties moved packages). Enable once Redisson ships a Spring Boot 4 compatible release.")
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}