package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.config.DedupKeyResolver;
import com.github.guoyyit.federatedsearchjava.model.SearchHit;
import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Component
public class ResultMerger {

    private final DedupKeyResolver dedupKeyResolver;
    private final ScoreNormalizer scoreNormalizer;
    private final Highlighter highlighter;
    private final SourceWeightRegistry weightRegistry;

    public ResultMerger(DedupKeyResolver dedupKeyResolver,
                        ScoreNormalizer scoreNormalizer,
                        Highlighter highlighter,
                        SourceWeightRegistry weightRegistry) {
        this.dedupKeyResolver = dedupKeyResolver;
        this.scoreNormalizer = scoreNormalizer;
        this.highlighter = highlighter;
        this.weightRegistry = weightRegistry;
    }

    public List<SearchHit> merge(Map<String, List<SourceHit>> hitsBySource, String query) {
        LinkedHashMap<String, Candidate> candidatesByKey = new LinkedHashMap<>();
        hitsBySource.forEach((sourceId, hits) -> {
            double weight = weightRegistry.getWeight(sourceId);
            for (SourceHit hit : hits) {
                double weightedScore = scoreNormalizer.weightedScore(hit, hits, weight);
                String key = dedupKeyResolver.resolve(hit);
                Candidate existing = candidatesByKey.get(key);
                if (existing == null) {
                    candidatesByKey.put(key, new Candidate(key, hit, weightedScore));
                } else {
                    existing.accept(hit, weightedScore, sourceId);
                }
            }
        });

        List<SearchHit> merged = new ArrayList<>();
        for (Candidate candidate : candidatesByKey.values()) {
            SourceHit best = candidate.hit;
            Highlighter.Result rendering = highlighter.highlight(best.title(), best.content(), query);
            SearchHit searchHit = new SearchHit(
                    candidate.key,
                    best.docId() == null ? candidate.key : best.docId(),
                    plainTitle(best),
                    rendering.highlightedTitle(),
                    rendering.snippet() == null ? "" : rendering.snippet(),
                    round(candidate.weightedScore),
                    List.copyOf(candidate.sources),
                    best.timestamp() == null ? Instant.EPOCH : best.timestamp()
            );
            merged.add(searchHit);
        }

        merged.sort(
                Comparator.comparingDouble(SearchHit::score).reversed()
                        .thenComparing(SearchHit::lastUpdated, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SearchHit::title, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SearchHit::key)
        );
        return List.copyOf(merged);
    }

    private String plainTitle(SourceHit hit) {
        if (hit.title() == null || hit.title().isBlank()) {
            return hit.docId() == null || hit.docId().isBlank() ? "Untitled" : hit.docId();
        }
        return hit.title().trim();
    }

    private double round(double value) {
        double scaled = Math.round(value * 1_000_000d) / 1_000_000d;
        return scaled;
    }

    private static final class Candidate {
        private final String key;
        private SourceHit hit;
        private double weightedScore;
        private final TreeSet<String> sources = new TreeSet<>();

        private Candidate(String key, SourceHit hit, double weightedScore) {
            this.key = key;
            this.hit = hit;
            this.weightedScore = weightedScore;
            this.sources.add(hit.sourceId());
        }

        private void accept(SourceHit incoming, double incomingScore, String sourceId) {
            sources.add(sourceId);
            if (incomingScore > weightedScore) {
                this.hit = incoming;
                this.weightedScore = incomingScore;
            }
        }
    }
}
