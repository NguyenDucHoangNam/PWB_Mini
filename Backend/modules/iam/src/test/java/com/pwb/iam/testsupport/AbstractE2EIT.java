package com.pwb.iam.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;
import com.pwb.iam.domain.service.GoogleTokenVerifierPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        classes = TestIamConfiguration.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "pwb.iam.seeder.enabled=false"
        }
)
@Import(AbstractE2EIT.TestBeans.class)
@ActiveProfiles("e2e")
public abstract class AbstractE2EIT {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("pwb_iam_test")
                    .withUsername("pwb_test")
                    .withPassword("pwb_test");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    protected int serverPort;

    @Autowired
    protected EmailDeliveryPort emailDeliveryPort;

    @Autowired
    protected org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    protected InMemoryEmailDeliveryAdapter inMemoryEmailAdapter() {
        return applicationContext.getBean(InMemoryEmailDeliveryAdapter.class);
    }

    protected StubGoogleTokenVerifier googleTokenVerifier() {
        return applicationContext.getBean(StubGoogleTokenVerifier.class);
    }

    @BeforeEach
    void resetState() {
        inMemoryEmailAdapter().clear();
        flushRedis();
    }

    protected void flushRedis() {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @Autowired
    protected ObjectMapper objectMapper;

    protected RestClient restClient;

    protected String latestOtpCode(String email) {
        return inMemoryEmailAdapter().sentEmails().stream()
                .filter(cmd -> cmd.variables() != null && cmd.variables().containsKey("code"))
                .filter(cmd -> email.equals(cmd.email()))
                .reduce((first, second) -> second)
                .map(cmd -> cmd.variables().get("code"))
                .orElseThrow(() -> new IllegalStateException("No OTP email found for " + email));
    }

    protected String latestResetLink(String email) {
        return inMemoryEmailAdapter().sentEmails().stream()
                .filter(cmd -> cmd.variables() != null && cmd.variables().containsKey("resetLink"))
                .filter(cmd -> email.equals(cmd.email()))
                .reduce((first, second) -> second)
                .map(cmd -> cmd.variables().get("resetLink"))
                .orElseThrow(() -> new IllegalStateException("No reset email found for " + email));
    }

    protected String extractResetToken(String resetLink) {
        int idx = resetLink.indexOf("token=");
        if (idx < 0) {
            throw new IllegalStateException("Reset link missing token: " + resetLink);
        }
        String token = resetLink.substring(idx + "token=".length());
        int amp = token.indexOf('&');
        return amp > 0 ? token.substring(0, amp) : token;
    }

    protected RestClient restClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl("http://localhost:" + serverPort)
                    .build();
        }
        return restClient;
    }

    static class TestBeans {

        @Bean
        @Primary
        EmailDeliveryPort emailDeliveryPort() {
            return new InMemoryEmailDeliveryAdapter();
        }

        @Bean
        @Primary
        GoogleTokenVerifierPort googleTokenVerifierPort() {
            return new StubGoogleTokenVerifier();
        }
    }
}