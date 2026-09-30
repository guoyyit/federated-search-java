package com.github.guoyyit.federatedsearchjava.model;

import java.time.Instant;

public record SourceHit(
        String sourceId,
        String docId,
        String title,
        String content,
        double rawScore,
        Instant timestamp
) {
}
