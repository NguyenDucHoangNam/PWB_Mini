package com.pwb.iam.domain.service;

import com.pwb.iam.domain.repository.UserSearchCriteria;

import java.util.List;
import java.util.Optional;

/**
 * The search engine's view of the user directory.
 *
 * <p>Both methods answer {@link Optional#empty()} when the engine is unavailable, which tells the caller
 * to run the existing specification query instead. Empty is not the same as an empty result.
 */
public interface UserSearchPort {

    Optional<UserSearchHits> search(UserSearchCriteria criteria, int from, int size);

    Optional<List<UserSuggestion>> suggest(UserSearchCriteria criteria, int limit);
}
