package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.config.SearchProperties;
import com.github.guoyyit.federatedsearchjava.config.WeightRegistry;
import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.engine.Deduplicator;
import com.github.guoyyit.federatedsearchjava.engine.Highlighter;
import com.github.guoyyit.federatedsearchjava.engine.ScoreNormalizer;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import com.github.guoyyit.federatedsearchjava.engine.SnapshotStore;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;

import java.util.List;
import java.util.Map;

/**
 * Builds a real coordinator pipeline around fake sources, with no Spring
 * context involved.
 */
final class CoordinatorTestSupport {

    private CoordinatorTestSupport() {
    }

    static SearchCoordinator coordinator(List<IndexSource> sources, int maxConcurrent) {
        return coordinator(sources, maxConcurrent, Map.of());
    }

    static SearchCoordinator coordinator(
            List<IndexSource> sources, int maxConcurrent, Map<String, Double> weights) {
        SearchProperties properties = new SearchProperties(
                100L,
                maxConcurrent,
                60L,
                "DOCUMENT_ID",
                160,
                "<em>",
                "</em>",
                List.of());
        WeightRegistry registry = new WeightRegistry(properties);
        weights.forEach(registry::updateWeight);
        return new SearchCoordinator(
                sources,
                properties,
                registry,
                new ScoreNormalizer(),
                new Deduplicator(),
                new Highlighter(160, "<em>", "</em>"),
                new SnapshotStore(60L));
    }

    static SearchHit hit(String source, String documentId, double rawScore) {
        return hit(source, documentId, "title about federated search",
                "content describing federated search merging",
                "https://" + source + ".local/" + documentId,
                rawScore, "hash-" + source + "-" + documentId);
    }

    static SearchHit hit(
            String source,
            String documentId,
            String title,
            String content,
            String url,
            double rawScore,
            String contentHash) {
        return new SearchHit(
                documentId,
                source,
                title,
                content,
                url,
                rawScore,
                rawScore,
                rawScore,
                null,
                null,
                Map.of(),
                System.currentTimeMillis(),
                contentHash);
    }
}
