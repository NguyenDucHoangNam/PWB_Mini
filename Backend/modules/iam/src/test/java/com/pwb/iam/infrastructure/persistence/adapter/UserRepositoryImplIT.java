package com.pwb.iam.infrastructure.persistence.adapter;

import com.pwb.iam.domain.model.EmailAddress;
import com.pwb.iam.domain.model.OAuthProvider;
import com.pwb.iam.domain.model.Password;
import com.pwb.iam.domain.model.Role;
import com.pwb.iam.domain.model.RoleName;
import com.pwb.iam.domain.model.User;
import com.pwb.iam.domain.model.UserStatus;
import com.pwb.iam.infrastructure.persistence.entity.RoleJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.RoleJpaRepository;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.iam.infrastructure.search.IamSearchIndexWriter;
import com.pwb.iam.testsupport.AbstractRepositoryIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("UserRepositoryImpl — H2 integration")
class UserRepositoryImplIT extends AbstractRepositoryIT {

    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private RoleJpaRepository roleJpaRepository;
    @Autowired private UserMapper userMapper;

    private UserRepositoryImpl repository;
    private RoleJpaEntity userRole;

    @BeforeEach
    void setUp() {
        // Every save now also feeds the search index. That path ends in Kafka via the outbox, which
        // this H2-only test has no business exercising — the indexing contract is covered by the
        // search module's own tests. Mocked so the repository behaviour stays the subject here.
        repository = new UserRepositoryImpl(userJpaRepository, userMapper, mock(IamSearchIndexWriter.class));
        userJpaRepository.deleteAll();
        roleJpaRepository.deleteAll();
        userRole = roleJpaRepository.save(RoleJpaEntity.builder()
                .name(RoleName.USER.name())
                .description("Standard user")
                .build());
    }

    @AfterEach
    void cleanUp() {
        userJpaRepository.deleteAll();
        roleJpaRepository.deleteAll();
    }

    private User newLocalUser(String email) {
        return User.createLocal(EmailAddress.of(email), Password.fromHash("hashed:Pass1234!@#"), "John", RoleName.USER);
    }

    @Test
    @DisplayName("should_save_local_user_and_find_by_id")
    void should_save_local_user_and_find_by_id() {
        User saved = repository.save(newLocalUser("alice@example.com"));

        assertThat(saved.getUserId()).isNotNull();
        assertThat(saved.getEmail().value()).isEqualTo("alice@example.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getOauthProvider()).isEqualTo(OAuthProvider.LOCAL);
        assertThat(saved.getRole()).isEqualTo(RoleName.USER);

        User found = repository.findById(saved.getUserId()).orElseThrow();
        assertThat(found.getEmail().value()).isEqualTo("alice@example.com");
        assertThat(found.getRole()).isEqualTo(RoleName.USER);
    }

    @Test
    @DisplayName("should_return_empty_when_id_not_found")
    void should_return_empty_when_id_not_found() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should_find_by_email_and_normalize_lowercase")
    void should_find_by_email_and_normalize_lowercase() {
        repository.save(newLocalUser("Bob@Example.com"));

        assertThat(repository.findByEmail("bob@example.com")).isPresent();
        assertThat(repository.findByEmail("BOB@EXAMPLE.COM")).isPresent();
    }

    @Test
    @DisplayName("should_return_empty_when_email_null")
    void should_return_empty_when_email_null() {
        assertThat(repository.findByEmail(null)).isEmpty();
    }

    @Test
    @DisplayName("should_check_existsByEmail_case_insensitive")
    void should_check_existsByEmail_case_insensitive() {
        repository.save(newLocalUser("alice@example.com"));

        assertThat(repository.existsByEmail("alice@example.com")).isTrue();
        assertThat(repository.existsByEmail("ALICE@EXAMPLE.COM")).isTrue();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("should_return_false_when_existsByEmail_called_with_null")
    void should_return_false_when_existsByEmail_called_with_null() {
        assertThat(repository.existsByEmail(null)).isFalse();
    }

    @Test
    @DisplayName("should_round_trip_status_through_find_by_id")
    void should_round_trip_status_through_find_by_id() {
        // findByIdAndStatus is gone: no caller wanted "this user, but only in that state", and the
        // one that looked like it did was really asking whether the account was still unverified.
        // What has to hold is that the status survives the round trip.
        User saved = repository.save(newLocalUser("carol@example.com"));

        User found = repository.findById(saved.getUserId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    @DisplayName("should_find_by_oauth_provider_and_oauth_id")
    void should_find_by_oauth_provider_and_oauth_id() {
        User google = repository.save(User.createGoogle(
                EmailAddress.of("dave@example.com"),
                "google-sub-123",
                "Dave",
                "https://example.com/avatar.png"));

        assertThat(repository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-123")).isPresent();
        assertThat(repository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "unknown")).isEmpty();
    }

    @Test
    @DisplayName("should_skip_soft_deleted_user_in_findById")
    void should_skip_soft_deleted_user_in_findById() {
        User saved = repository.save(newLocalUser("ed@example.com"));
        UUID id = saved.getUserId();
        userJpaRepository.findById(id).ifPresent(e -> {
            e.setDeleted(true);
            userJpaRepository.saveAndFlush(e);
        });

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsByEmail("ed@example.com")).isFalse();
    }

    @Test
    @DisplayName("should_update_existing_user_when_save_called_twice")
    void should_update_existing_user_when_save_called_twice() {
        User first = repository.save(newLocalUser("frank@example.com"));
        UUID id = first.getUserId();
        first.markActive();

        User second = repository.save(first);

        assertThat(second.getUserId()).isEqualTo(id);
        assertThat(second.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userJpaRepository.count()).isEqualTo(1L);
    }
}