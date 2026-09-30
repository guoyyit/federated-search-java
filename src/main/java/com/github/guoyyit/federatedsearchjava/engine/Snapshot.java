package com.github.guoyyit.federatedsearchjava.engine;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;

import java.util.List;

/**
 * Immutable, timestamped result list backing one logical paged query.
 */
public record Snapshot(
        String id,
        String query,
        DedupMode dedupMode,
        List<SearchHit> hits,
        long createdAtMillis,
        long expiresAtMillis
) {
}
