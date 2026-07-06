package com.pwb.backend.shared.config;

import com.pwb.backend.iam.internal.config.JwtAuthenticationFilter;
import com.pwb.backend.iam.internal.controller.AuthController;
import com.pwb.backend.iam.internal.service.AuthService;
import com.pwb.backend.iam.internal.service.JwtService;
import com.pwb.backend.shared.exception.BusinessException;
import com.pwb.backend.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pwb.backend.shared.exception.GlobalExceptionHandler;
import org.springframework.context.annotation.Import;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({I18nConfig.class, GlobalExceptionHandler.class})
class I18nIntegrationTest {


  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private JwtService jwtService;

  @MockitoBean
  private StringRedisTemplate redisTemplate;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @Autowired
  private MessageSource messageSource;

  @Test
  void testI18n_defaultVietnamese() throws Exception {
    when(authService.login(any(), any()))
        .thenThrow(new BusinessException(ErrorCode.BAD_CREDENTIALS));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"usernameOrEmail\":\"test\",\"password\":\"pass\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
  }

  @Test
  void testI18n_englishLanguageHeader() throws Exception {
    when(authService.login(any(), any()))
        .thenThrow(new BusinessException(ErrorCode.BAD_CREDENTIALS));

    mockMvc.perform(post("/api/v1/auth/login")
            .header("Accept-Language", "en")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"usernameOrEmail\":\"test\",\"password\":\"pass\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Incorrect username or password."));
  }
}

