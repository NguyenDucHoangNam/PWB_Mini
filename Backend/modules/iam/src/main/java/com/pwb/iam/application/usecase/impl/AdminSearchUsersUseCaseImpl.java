package com.pwb.iam.application.usecase.impl;

import com.pwb.iam.application.dto.AdminUserSuggestionView;
import com.pwb.iam.application.dto.AdminUserView;
import com.pwb.iam.application.usecase.AdminSearchUsersUseCase;
import com.pwb.iam.domain.repository.UserSearchCriteria;
import com.pwb.iam.domain.service.UserSearchHits;
import com.pwb.iam.domain.service.UserSearchPort;
import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.iam.infrastructure.persistence.mapper.UserMapper;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.iam.infrastructure.persistence.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSearchUsersUseCaseImpl implements AdminSearchUsersUseCase {

    private static final int MAX_SUGGESTIONS = 20;

    private final UserSearchPort userSearchPort;
    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public Page<AdminUserView> search(UUID adminId, UserSearchCriteria criteria, Pageable pageable) {
        Optional<UserSearchHits> hits = userSearchPort.search(
                criteria, (int) pageable.getOffset(), pageable.getPageSize());

        if (hits.isEmpty()) {
            log.debug("SEARCH.users fallback to database");
            return userJpaRepository
                    .findAll(UserSpecifications.fromCriteria(criteria), pageable)
                    .map(this::toView);
        }
        return toPage(hits.get(), pageable);
    }

    @Override
    public List<AdminUserSuggestionView> suggest(UUID adminId, UserSearchCriteria criteria, int limit) {
        if (criteria.keyword() == null || criteria.keyword().isBlank()) {
            return List.of();
        }
        int capped = Math.clamp(limit, 1, MAX_SUGGESTIONS);

        return userSearchPort.suggest(criteria, capped)
                .map(suggestions -> suggestions.stream()
                        .map(s -> new AdminUserSuggestionView(s.id(), s.email(), s.fullName()))
                        .toList())
                .orElseGet(() -> suggestFromDatabase(criteria, capped));
    }

    /**
     * The engine ranked the ids; the rows come from Postgres so nothing renders from a stale copy. The
     * soft-delete filter is already applied on both sides, and {@code findByIdInAndDeletedFalse} applies
     * it once more here — an id the index has not caught up on simply drops out of the page.
     */
    private Page<AdminUserView> toPage(UserSearchHits hits, Pageable pageable) {
        Map<UUID, UserJpaEntity> byId = userJpaRepository.findByIdInAndDeletedFalse(hits.ids()).stream()
                .collect(Collectors.toMap(UserJpaEntity::getId, Function.identity()));

        List<AdminUserView> ordered = hits.ids().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toView)
                .toList();

        return new PageImpl<>(ordered, pageable, hits.total());
    }

    private List<AdminUserSuggestionView> suggestFromDatabase(UserSearchCriteria criteria, int limit) {
        return userJpaRepository
                .findAll(UserSpecifications.fromCriteria(criteria), PageRequest.of(0, limit))
                .getContent().stream()
                .map(entity -> new AdminUserSuggestionView(
                        entity.getId(), entity.getEmail(), entity.getFullName()))
                .toList();
    }

    private AdminUserView toView(UserJpaEntity entity) {
        return AdminUserView.from(
                userMapper.toDomain(entity), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
