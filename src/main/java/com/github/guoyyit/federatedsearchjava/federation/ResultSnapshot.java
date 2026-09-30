package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.model.SearchHit;
import com.github.guoyyit.federatedsearchjava.model.SourceDegradation;

import java.time.Instant;
import java.util.List;

public record ResultSnapshot(
        String id,
        String query,
        Instant createdAt,
        List<SearchHit> hits,
        List<SourceDegradation> degradations,
        boolean complete
) {
}
