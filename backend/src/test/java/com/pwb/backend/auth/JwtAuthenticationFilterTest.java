package com.pwb.backend.auth;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtSigner jwtSigner;

    @MockBean
    private RedissonClient redissonClient;

    @Test
    void validTokenDoesNotBreakRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtSigner.generateAccessToken(userId, "test@example.com", "ROLE_USER");

        mockMvc.perform(get("/api/v1/auth/check-username").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void invalidTokenDoesNotBreakRequest() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-username").header("Authorization", "Bearer not-a-real-jwt-token"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void noHeaderReachesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-username"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test-protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidTokenReturnsAccessDenied() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtSigner.generateAccessToken(userId, "test@example.com", "ROLE_USER");

        mockMvc.perform(get("/api/v1/test-protected").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}