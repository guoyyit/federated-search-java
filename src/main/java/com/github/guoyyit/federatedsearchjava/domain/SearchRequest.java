package com.github.guoyyit.federatedsearchjava.domain;

import com.github.guoyyit.federatedsearchjava.engine.DedupMode;

/**
 * A single federated query. A null {@code dedupMode}/{@code timeoutMs} means
 * "use service default".
 */
public record SearchRequest(
        String query,
        int page,
        int pageSize,
        DedupMode dedupMode,
        Long timeoutMs,
        String snapshotId
) {
}
