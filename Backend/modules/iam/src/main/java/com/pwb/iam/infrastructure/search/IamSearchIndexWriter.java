package com.pwb.iam.infrastructure.search;

import com.pwb.iam.infrastructure.persistence.entity.UserJpaEntity;
import com.pwb.infra.search.SearchIndexNames;
import com.pwb.infra.search.SearchIndexPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the user index in step with the table. Hooked into the repository adapter's save, which every
 * write goes through: registration, verification, ban, unban, role change and profile edits alike.
 *
 * <p>A soft delete is an ordinary save with the flag set, so it arrives here as an upsert and the query
 * side filters it out. There is no separate delete path.
 */
@Component
@RequiredArgsConstructor
public class IamSearchIndexWriter {

    private final SearchIndexPublisher publisher;

    public void userSaved(UserJpaEntity entity) {
        publisher.upsert(
                SearchIndexNames.USERS,
                entity.getId().toString(),
                UserSearchDocument.from(entity));
    }
}
