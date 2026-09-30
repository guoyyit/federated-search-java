package com.github.guoyyit.federatedsearchjava.local;

import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import com.github.guoyyit.federatedsearchjava.source.SearchIndexSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public abstract class AbstractInMemorySource implements SearchIndexSource {

    protected final String sourceId;
    protected final List<Doc> docs;
    private final long latencyMillis;

    protected AbstractInMemorySource(String sourceId, List<Doc> docs) {
        this(sourceId, docs, 0L);
    }

    protected AbstractInMemorySource(String sourceId, List<Doc> docs, long latencyMillis) {
        this.sourceId = sourceId;
        this.docs = List.copyOf(docs);
        this.latencyMillis = Math.max(0L, latencyMillis);
    }

    @Override
    public String getId() {
        return sourceId;
    }

    @Override
    public List<SourceHit> search(String query) throws InterruptedException {
        if (latencyMillis > 0) {
            Thread.sleep(latencyMillis);
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> terms = terms(query);
        List<Scored> matched = new ArrayList<>();
        for (Doc doc : docs) {
            String title = doc.title() == null ? "" : doc.title().toLowerCase(Locale.ROOT);
            String content = doc.content() == null ? "" : doc.content().toLowerCase(Locale.ROOT);
            double score = 0.0;
            int matchedTerms = 0;
            for (String term : terms) {
                int titleHits = countOccurrences(title, term);
                int contentHits = countOccurrences(content, term);
                if (titleHits > 0 || contentHits > 0) {
                    matchedTerms++;
                    score += titleHits * 3.0 + contentHits * 1.0;
                }
            }
            if (matchedTerms > 0) {
                matched.add(new Scored(doc, (score + doc.scoreBoost()) * matchedTerms));
            }
        }
        matched.sort(Comparator.comparingDouble((Scored scored) -> scored.score).reversed()
                .thenComparing(scored -> scored.doc.docId()));
        List<SourceHit> hits = new ArrayList<>(matched.size());
        for (Scored scored : matched) {
            Doc doc = scored.doc;
            hits.add(new SourceHit(
                    sourceId,
                    doc.docId(),
                    doc.title(),
                    doc.content(),
                    scored.score,
                    doc.timestamp() == null ? Instant.EPOCH : doc.timestamp()
            ));
        }
        return hits;
    }

    private List<String> terms(String query) {
        List<String> terms = new ArrayList<>();
        for (String token : query.trim().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Digit}]+")) {
            if (!token.isEmpty()) {
                terms.add(token);
            }
        }
        return terms;
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += Math.max(1, needle.length());
        }
        return count;
    }

    public record Doc(String docId, String title, String content, double scoreBoost, Instant timestamp) {
    }

    private record Scored(Doc doc, double score) {
    }
}
