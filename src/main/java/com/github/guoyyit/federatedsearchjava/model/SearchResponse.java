package com.github.guoyyit.federatedsearchjava.model;

import java.util.List;

public record SearchResponse(
        String query,
        String snapshotId,
        int page,
        int pageSize,
        int totalHits,
        List<SearchHit> hits,
        boolean complete,
        List<SourceDegradation> degradations
) {
}
