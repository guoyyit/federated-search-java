package com.github.guoyyit.federatedsearchjava.domain;

import java.util.Map;

/**
 * One merged hit. {@code rawScore} is the source-native score,
 * {@code normalizedScore} is the per-source score rescaled to [0,1] and
 * {@code finalScore} = normalizedScore * source weight.
 */
public record SearchHit(
        String documentId,
        String source,
        String title,
        String content,
        String url,
        double rawScore,
        double normalizedScore,
        double finalScore,
        String highlightedTitle,
        String snippet,
        Map<String, String> attributes,
        long indexedAt,
        String contentHash
) {
}
