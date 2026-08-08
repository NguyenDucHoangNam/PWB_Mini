package com.pwb.iam.domain.service;

import java.util.List;
import java.util.UUID;

/** Ids in relevance order plus the total match count; the rows themselves come from Postgres. */
public record UserSearchHits(List<UUID> ids, long total) {
}
