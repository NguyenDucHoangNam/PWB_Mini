package com.pwb.infra.search;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "pwb.search")
public class SearchConfig {

    /**
     * Turns the whole search stack off. With this false the gateway answers empty for every query, which
     * every caller already handles by falling back to Postgres, and nothing is indexed. Meant for
     * environments that do not run Elasticsearch at all rather than for one that is merely down.
     */
    private boolean enabled = true;

    /**
     * Prefixed onto every logical index name, so one cluster can host several environments without them
     * writing over each other.
     */
    private String indexPrefix = "pwb";

    /** Documents per bulk request during a reindex. */
    private int bulkSize = 100;

    /** Rows read from Postgres per page during a reindex. */
    private int reindexPageSize = 200;
}
