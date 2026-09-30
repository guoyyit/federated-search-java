package com.github.guoyyit.federatedsearchjava.model;

import java.time.Instant;
import java.util.List;

public record SearchHit(
        String key,
        String docId,
        String title,
        String highlightedTitle,
        String snippet,
        double score,
        List<String> matchedSources,
        Instant lastUpdated
) {
}
