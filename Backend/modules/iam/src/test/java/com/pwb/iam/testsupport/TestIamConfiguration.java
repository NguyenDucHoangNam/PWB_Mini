package com.pwb.iam.testsupport;

import com.pwb.iam.infrastructure.config.AvatarProperties;
import com.pwb.iam.infrastructure.config.GoogleProperties;
import com.pwb.iam.infrastructure.config.IamJpaConfig;
import com.pwb.iam.infrastructure.config.IamPolicyConfig;
import com.pwb.iam.infrastructure.config.JwtProperties;
import com.pwb.iam.infrastructure.config.LoginPolicyProperties;
import com.pwb.iam.infrastructure.config.OtpProperties;
import com.pwb.iam.infrastructure.config.PasswordPolicyProperties;
import com.pwb.iam.infrastructure.config.PasswordResetProperties;
import com.pwb.iam.infrastructure.config.RateLimitProperties;
import com.pwb.iam.infrastructure.config.RefreshTokenProperties;
import com.pwb.iam.infrastructure.config.SecurityProperties;
import com.pwb.iam.infrastructure.config.SeederProperties;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.iam.domain.service.UserSearchHits;
import com.pwb.iam.domain.service.UserSearchPort;
import com.pwb.iam.domain.service.UserSuggestion;
import com.pwb.iam.infrastructure.persistence.adapter.RoleRepositoryImpl;
import com.pwb.iam.infrastructure.search.IamSearchIndexWriter;
import com.pwb.infra.search.SearchReindexService;
import com.pwb.infra.storage.StorageService;
import java.util.List;
import java.util.Optional;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration.class,
        org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class,
        com.pwb.infra.config.InfraAutoConfiguration.class,
        com.pwb.iam.infrastructure.config.IamAutoConfiguration.class
})
@EntityScan(basePackages = "com.pwb.iam.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.pwb.iam.infrastructure.persistence.repository")
@EnableTransactionManagement
// Must list every @ConfigurationProperties class in the module that a scanned bean can reach, not
// just the ones a given test reads: IamPolicyConfig turns several of them into policy beans that
// controllers depend on, so a missing entry fails the whole module's contexts rather than one test.
@EnableConfigurationProperties({
        OtpProperties.class,
        PasswordPolicyProperties.class,
        JwtProperties.class,
        RefreshTokenProperties.class,
        LoginPolicyProperties.class,
        RateLimitProperties.class,
        PasswordResetProperties.class,
        GoogleProperties.class,
        SecurityProperties.class,
        SeederProperties.class,
        AvatarProperties.class
})
@ComponentScan(
        basePackages = {
                "com.pwb.iam.api",
                "com.pwb.iam.application",
                "com.pwb.iam.infrastructure.persistence.mapper",
                "com.pwb.iam.infrastructure.config",
                "com.pwb.iam.infrastructure.service.impl",
                "com.pwb.iam.infrastructure.security",
                "com.pwb.iam.infrastructure.security.jwt",
                "com.pwb.iam.infrastructure.mail",
                "com.pwb.iam.infrastructure.crypto",
                "com.pwb.iam.infrastructure.persistence.adapter",
                "com.pwb.web.security"
        },
        // Anything scanned here that depends on a bean from the excluded auto-configurations above
        // takes down every context in the module, not just its own test: the first failure trips
        // Spring's context failure threshold and every later class reports "skipping repeated
        // attempt" instead of its real result. The search collaborators are supplied as beans below
        // rather than excluded here, because the classes needing them are ordinary application
        // beans that the rest of the suite does exercise.
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.IamAutoConfiguration",
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.UserSeederService",
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.JacksonConfig",
                        "com\\.pwb\\.iam\\.infrastructure\\.config\\.EmailTemplateConfig"
                }
        )
)
@Import({
        IamJpaConfig.class,
        IamPolicyConfig.class,
        RoleRepositoryImpl.class
})
public class TestIamConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * Stand-ins for the infrastructure this configuration deliberately does not start.
     *
     * InfraAutoConfiguration is excluded above, and com.pwb.iam.infrastructure.search is left out of
     * the scan, because both ultimately want a live Elasticsearch and S3. Scanned application beans
     * still depend on four types from them, and any single one missing fails *every* context in the
     * module rather than just the test that needed it:
     *
     *   UserRepositoryImpl           -> IamSearchIndexWriter   (com.pwb.iam.infrastructure.search)
     *   AdminSearchUsersUseCaseImpl  -> UserSearchPort         (domain port, ES adapter not scanned)
     *   AdminSearchController        -> SearchReindexService   (com.pwb.infra.search)
     *   AvatarUrlResolver            -> StorageService         (com.pwb.infra.storage)
     *
     * Found by listing every com.pwb.infra.* type injected into the scanned packages, not by fixing
     * them one failure at a time — the context failure threshold reveals only the first one, so
     * iterating on the error message costs a full run per bean and still ends early.
     */

    /**
     * Written out rather than mocked because the empty Optional is meaningful: it is the port's own
     * "search is unavailable" signal, and callers answer it by falling back to Postgres. A mock
     * would return the same value while saying nothing about why.
     */
    @Bean
    public UserSearchPort userSearchPort() {
        return new UserSearchPort() {
            @Override
            public Optional<UserSearchHits> search(UserSearchCriteria criteria, int from, int size) {
                return Optional.empty();
            }

            @Override
            public Optional<List<UserSuggestion>> suggest(UserSearchCriteria criteria, int limit) {
                return Optional.empty();
            }
        };
    }

    /** Indexing is fire-and-forget; the tests assert on Postgres state, never on the index. */
    @Bean
    public IamSearchIndexWriter iamSearchIndexWriter() {
        return Mockito.mock(IamSearchIndexWriter.class);
    }

    /** Only reachable through /api/v1/admin/search, which no test in this module exercises. */
    @Bean
    public SearchReindexService searchReindexService() {
        return Mockito.mock(SearchReindexService.class);
    }

    /** Avatar URLs are presigned S3 links; no assertion in this module looks at one. */
    @Bean
    public StorageService storageService() {
        return Mockito.mock(StorageService.class);
    }
}