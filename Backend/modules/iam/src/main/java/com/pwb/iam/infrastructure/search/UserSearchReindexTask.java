package com.pwb.iam.infrastructure.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pwb.iam.infrastructure.persistence.repository.UserJpaRepository;
import com.pwb.infra.search.SearchAction;
import com.pwb.infra.search.SearchIndexEvent;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchReindexTask;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Rebuilds the user index from the table, soft-deleted rows included: the query side filters them, and
 * indexing them keeps a row that is later restored from being invisible until the next reindex.
 */
@Component
@RequiredArgsConstructor
public class UserSearchReindexTask implements SearchReindexTask {

    private final UserJpaRepository userJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String indexName() {
        return SearchIndexNames.USERS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchIndexEvent> readPage(int page, int size) {
        return userJpaRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(entity -> new SearchIndexEvent(
                        SearchIndexNames.USERS,
                        entity.getId().toString(),
                        SearchAction.UPSERT,
                        objectMapper.valueToTree(UserSearchDocument.from(entity))))
                .getContent();
    }
}
