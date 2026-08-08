package com.pwb.infra.search;

import java.util.List;

/**
 * How one index is rebuilt from Postgres. Implemented by the module that owns the data, since only it
 * knows how to read the table and shape the document.
 *
 * <p>Needed for the first rollout, when the tables already hold rows that no write event will ever fire
 * for, and as the way back from any drift between the database and the index.
 */
public interface SearchReindexTask {

    /** Logical index name; matches the {@link SearchIndexDefinition} for the same index. */
    String indexName();

    /**
     * Reads one page of rows and turns them into upsert events. An empty list ends the run, so pages must
     * be ordered stably — by primary key, not by a column a concurrent write could change.
     */
    List<SearchIndexEvent> readPage(int page, int size);
}
