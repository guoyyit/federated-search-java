package com.github.guoyyit.federatedsearchjava.engine;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

    /**
     * Per-source min-max normalization so native score scales become comparable.
     * For each source independently, score' = (score - min) / (max - min);
     * a zero range maps to 1.0 so a source's single/best hit is not erased.
     */
    @Component
    public class ScoreNormalizer {

    public List<SearchHit> normalize(List<SearchHit> hits) {
        Map<String, double[]> rangesBySource = new HashMap<>();
        for (SearchHit hit : hits) {
            double[] range = rangesBySource.computeIfAbsent(hit.source(), key -> new double[]{Double.MAX_VALUE, -Double.MAX_VALUE});
            range[0] = Math.min(range[0], hit.rawScore());
            range[1] = Math.max(range[1], hit.rawScore());
        }

        List<SearchHit> normalized = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            double[] range = rangesBySource.get(hit.source());
            double min = range[0];
            double max = range[1];
            double value = max == min ? 1.0d : (hit.rawScore() - min) / (max - min);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                value = 1.0d;
            }
            value = Math.max(0.0d, Math.min(1.0d, value));
            normalized.add(new SearchHit(
                    hit.documentId(),
                    hit.source(),
                    hit.title(),
                    hit.content(),
                    hit.url(),
                    hit.rawScore(),
                    value,
                    hit.finalScore(),
                    hit.highlightedTitle(),
                    hit.snippet(),
                    hit.attributes(),
                    hit.indexedAt(),
                    hit.contentHash()));
        }
        return normalized;
    }
}
