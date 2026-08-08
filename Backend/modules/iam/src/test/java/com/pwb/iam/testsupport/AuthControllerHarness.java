package com.pwb.iam.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.iam.api.controller.AuthController;
import com.pwb.iam.api.exception.IamExceptionHandler;
import com.pwb.iam.application.command.ChangePasswordCommand;
import com.pwb.iam.application.command.ForgotPasswordCommand;
import com.pwb.iam.application.command.GoogleLoginCommand;
import com.pwb.iam.application.command.LoginCommand;
import com.pwb.iam.application.command.RefreshTokenCommand;
import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.command.ResetPasswordCommand;
import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.service.AvatarUrlResolver;
import com.pwb.iam.application.usecase.ChangePasswordUseCase;
import com.pwb.iam.application.usecase.ForgotPasswordUseCase;
import com.pwb.iam.application.usecase.GoogleLoginUseCase;
import com.pwb.iam.application.usecase.LoginResult;
import com.pwb.iam.application.usecase.LoginUseCase;
import com.pwb.iam.application.usecase.LogoutUseCase;
import com.pwb.iam.application.usecase.RefreshTokenUseCase;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.application.usecase.ResetPasswordUseCase;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.infrastructure.config.RefreshTokenProperties;
import com.pwb.web.exception.GlobalExceptionHandler;
import com.pwb.web.message.MessageResolver;
import com.pwb.web.security.AuthenticatedUser;
import com.pwb.web.security.CurrentClientIpArgumentResolver;
import com.pwb.web.security.CurrentUserAgentArgumentResolver;
import com.pwb.web.security.CurrentUserArgumentResolver;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wires {@link AuthController} with mocked use cases behind a standalone {@link MockMvc}.
 *
 * <p>Hand-built rather than {@code @WebMvcTest}: the module's only {@code @SpringBootConfiguration}
 * lives in this package, and Boot's search from the test's package upwards looks at each package
 * without descending into it, so the slice never finds one. Building the context here also keeps
 * these tests off the JPA and security wiring that neither of them is about.
 *
 * <p>A real {@link GenericWebApplicationContext} rather than {@code standaloneSetup}: the standalone
 * builder gives the handler adapter a bean factory that cannot resolve embedded values, and
 * {@code /refresh} reads its cookie through {@code @CookieValue("${pwb.iam.refresh-token.cookie.name:…}")}.
 * Under standalone that name stays an unexpanded placeholder and every cookie test passes for the
 * wrong reason.
 */
public final class AuthControllerHarness {

    public static final String COOKIE_NAME = "pwb_refresh_token";

    public final RegisterUseCase registerUseCase = mock(RegisterUseCase.class);
    public final VerifyOtpUseCase verifyOtpUseCase = mock(VerifyOtpUseCase.class);
    public final ResendOtpUseCase resendOtpUseCase = mock(ResendOtpUseCase.class);
    public final LoginUseCase loginUseCase = mock(LoginUseCase.class);
    public final RefreshTokenUseCase refreshTokenUseCase = mock(RefreshTokenUseCase.class);
    public final LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
    public final GoogleLoginUseCase googleLoginUseCase = mock(GoogleLoginUseCase.class);
    public final ForgotPasswordUseCase forgotPasswordUseCase = mock(ForgotPasswordUseCase.class);
    public final ResetPasswordUseCase resetPasswordUseCase = mock(ResetPasswordUseCase.class);
    public final ChangePasswordUseCase changePasswordUseCase = mock(ChangePasswordUseCase.class);
    public final AvatarUrlResolver avatarUrlResolver = mock(AvatarUrlResolver.class);

    public final ObjectMapper objectMapper = new ObjectMapper();
    public final MockMvc mockMvc;
    public final User user = TestUserBuilder.localActive();
    public final UUID userId = user.getUserId();

    public AuthControllerHarness() {
        MessageSource messageSource = messageSource();
        AuthController controller = new AuthController(
                registerUseCase, verifyOtpUseCase, resendOtpUseCase, loginUseCase, refreshTokenUseCase,
                logoutUseCase, googleLoginUseCase, forgotPasswordUseCase, resetPasswordUseCase,
                changePasswordUseCase, avatarUrlResolver, new MessageResolver(messageSource),
                new RefreshTokenProperties());

        GenericWebApplicationContext wac = new GenericWebApplicationContext(new MockServletContext());
        ConfigurableListableBeanFactory beanFactory = wac.getBeanFactory();
        beanFactory.registerSingleton("authController", controller);
        beanFactory.registerSingleton("iamExceptionHandler", new IamExceptionHandler(messageSource));
        beanFactory.registerSingleton("globalExceptionHandler", new GlobalExceptionHandler(messageSource));
        new AnnotatedBeanDefinitionReader(wac).register(MvcConfig.class);
        wac.refresh();

        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();

        stubHappyPath();
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfig implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new CurrentUserArgumentResolver());
            resolvers.add(new CurrentClientIpArgumentResolver());
            resolvers.add(new CurrentUserAgentArgumentResolver());
        }
    }

    /**
     * Every endpoint succeeds by default so each test only has to say what it wants to go wrong.
     * {@code avatarUrlResolver} is deliberately left alone — the test user has no avatar, and the
     * mock's null is the answer the controller should be able to carry through.
     */
    private void stubHappyPath() {
        when(registerUseCase.execute(any(RegisterCommand.class))).thenReturn(user);
        when(verifyOtpUseCase.execute(any(VerifyOtpCommand.class))).thenReturn(loginResult());
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(loginResult());
        when(refreshTokenUseCase.execute(any(RefreshTokenCommand.class))).thenReturn(loginResult());
        when(googleLoginUseCase.execute(any(GoogleLoginCommand.class))).thenReturn(loginResult());
        when(forgotPasswordUseCase.execute(any(ForgotPasswordCommand.class)))
                .thenReturn(ForgotPasswordUseCase.Result.sent(userId, 60));
        when(resetPasswordUseCase.execute(any(ResetPasswordCommand.class)))
                .thenReturn(new ResetPasswordUseCase.Result(userId));
        when(changePasswordUseCase.execute(any(ChangePasswordCommand.class)))
                .thenReturn(new ChangePasswordUseCase.Result(userId));
    }

    public LoginResult loginResult() {
        StubTokenManagerService tokens = new StubTokenManagerService(userId);
        return new LoginResult(user, tokens.issueAccessToken(user), tokens.issueRefreshToken(userId));
    }

    /** Puts the harness user in the security context, which is where {@code @CurrentUser} reads from. */
    public void authenticate() {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId.toString())
                .email("active@example.com")
                .authorities(Set.of("ROLE_USER"))
                .isOAuthUser(false)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    public String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages/messages", "iam/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }
}
