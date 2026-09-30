package com.github.guoyyit.federatedsearchjava.domain;

import java.util.List;

/**
 * Federated response. {@code complete} is true only when every enabled source
 * answered COMPLETE; {@code degraded} is the inverse and each degradation is
 * explained in {@code reports}.
 */
public record SearchResponse(
        String query,
        int page,
        int pageSize,
        int totalHits,
        boolean complete,
        boolean degraded,
        List<SourceReport> sourceReports,
        List<SearchHit> hits,
        String snapshotId
) {
}
