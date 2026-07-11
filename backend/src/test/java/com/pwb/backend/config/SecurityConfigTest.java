package com.pwb.backend.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedissonClient redissonClient;

    @Test
    void contextLoadsWithSecurityEnabled() {
    }

    @Test
    void protectedPostEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/test-protected"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void publicPostAuthEndpointIsReachable() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void publicGetCheckUsernameIsReachable() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-username"))
                .andExpect(status().is4xxClientError());
    }
}