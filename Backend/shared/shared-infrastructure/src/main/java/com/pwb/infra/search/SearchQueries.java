package com.pwb.infra.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.Collection;
import java.util.List;

/**
 * The query shapes every index here uses, so relevance behaves the same on all four search screens.
 */
public final class SearchQueries {

    private SearchQueries() {
    }

    /**
     * What a user means by typing a few words: the whole term, a typo of it, or the start of it.
     *
     * <p>All three are {@code should} clauses rather than one {@code multi_match}, because each wants a
     * different boost — an exact hit has to outrank a fuzzy one, or a search for "remix" ranks "remit"
     * alongside it. Diacritics are handled by the analyzer, not here: both the field and the query run
     * through {@code asciifolding}, which is what lets "ha noi" find "Hà Nội".
     */
    public static Query keywordMatch(String field, String keyword) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.match(m -> m.field(field).query(keyword).boost(3.0f)))
                .should(s -> s.match(m -> m.field(field).query(keyword).fuzziness("AUTO").boost(1.0f)))
                .should(s -> s.matchPhrasePrefix(m -> m.field(field).query(keyword).boost(2.0f)))
                .minimumShouldMatch("1")));
    }

    /**
     * Prefix matching for search-as-you-type. Runs against the {@code .autocomplete} sub-field, whose
     * index-time analyzer emits edge n-grams while its search analyzer does not — so "ng" matches
     * "nguyen" without "nguyen" also matching everything starting with "n".
     */
    public static Query autocomplete(String field, String keyword) {
        return Query.of(q -> q.match(m -> m.field(field + ".autocomplete").query(keyword)));
    }

    /** Restricts results to one owner. Belongs in a {@code filter} clause: it must not affect scoring. */
    public static Query ownedBy(String field, String ownerId) {
        return Query.of(q -> q.term(t -> t.field(field).value(ownerId)));
    }

    public static Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    public static Query term(String field, boolean value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    public static Query terms(String field, Collection<String> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
        return Query.of(q -> q.terms(t -> t.field(field).terms(v -> v.value(fieldValues))));
    }

    /** Either bound may be null, which leaves that side open. */
    public static Query range(String field, Integer min, Integer max) {
        return Query.of(q -> q.range(r -> r.number(n -> {
            n.field(field);
            if (min != null) {
                n.gte(min.doubleValue());
            }
            if (max != null) {
                n.lte(max.doubleValue());
            }
            return n;
        })));
    }

    /**
     * Relevance first, newest first among equally relevant hits. Without the tie-break, two documents on
     * the same score come back in whatever order the shard merge produced, so paging could repeat or skip
     * one of them.
     */
    public static List<SortOptions> byScoreThen(String dateField) {
        return List.of(
                SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field(dateField).order(SortOrder.Desc)))
        );
    }
}
