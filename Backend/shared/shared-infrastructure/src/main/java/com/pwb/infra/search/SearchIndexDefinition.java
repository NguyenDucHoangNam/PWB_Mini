package com.pwb.infra.search;

/**
 * One index a module owns. Every implementation registered as a bean is created on startup by
 * {@link SearchIndexBootstrapper}, which pairs the module's mappings with the analyzer settings shared
 * across the whole cluster.
 */
public interface SearchIndexDefinition {

    /** Logical name, without the configured prefix — for example {@code songs}. */
    String indexName();

    /**
     * Classpath location of a JSON file holding only the {@code properties} block of the mapping. The
     * settings half comes from {@link SearchIndexBootstrapper#SHARED_SETTINGS_RESOURCE}, so no module
     * restates the analyzers.
     */
    String mappingResource();
}
