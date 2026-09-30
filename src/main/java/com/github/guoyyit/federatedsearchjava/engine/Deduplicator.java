package com.github.guoyyit.federatedsearchjava.engine;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges hits from different sources. The winner among duplicates is the hit
 * with the highest finalScore; ties are broken deterministically by source
 * name then document id. Merge result is order-independent.
 */
@Component
public class Deduplicator {

    private static final Comparator<SearchHit> WINNER_ORDER =
            Comparator.comparingDouble(SearchHit::finalScore)
                    .reversed()
                    .thenComparing(SearchHit::source)
                    .thenComparing(SearchHit::documentId);

    /**
     * Final ranking. It only depends on hit values, never on which source
     * answered first or on iteration/arrival order.
     */
    public static final Comparator<SearchHit> RANK_ORDER =
            WINNER_ORDER;

    public List<SearchHit> merge(List<SearchHit> hits, DedupMode mode) {
        Map<String, SearchHit> winners = new LinkedHashMap<>();
        for (SearchHit hit : hits) {
            String key = dedupKey(hit, mode);
            SearchHit current = winners.get(key);
            if (current == null || WINNER_ORDER.compare(hit, current) < 0) {
                winners.put(key, hit);
            }
        }
        List<SearchHit> merged = new java.util.ArrayList<>(winners.values());
        merged.sort(RANK_ORDER);
        return merged;
    }

    private static String dedupKey(SearchHit hit, DedupMode mode) {
        String value = switch (mode) {
            case CONTENT_HASH -> nullToEmpty(hit.contentHash());
            case URL -> nullToEmpty(hit.url());
            case DOCUMENT_ID -> nullToEmpty(hit.documentId());
        };
        if (value.isEmpty()) {
            // A missing key value must never merge unrelated documents:
            // fall back to a source-scoped unique key.
            return "fallback:" + hit.source() + ":" + hit.documentId();
        }
        return mode.name() + ":" + value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
