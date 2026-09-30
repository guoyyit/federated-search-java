package com.github.guoyyit.federatedsearchjava.local;

import com.github.guoyyit.federatedsearchjava.config.SearchProperties;
import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.engine.CancelledException;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base for the in-memory index sources: keyword overlap scoring against a
 * local document list, with a configurable artificial delay so timeout and
 * concurrency behaviour is testable. Each subclass only provides the corpus.
 */
public abstract class AbstractLocalIndexSource implements IndexSource {

    private final SearchProperties properties;
    private final long delayMillis;

    protected AbstractLocalIndexSource(SearchProperties properties) {
        this(properties, 0L);
    }

    protected AbstractLocalIndexSource(SearchProperties properties, long delayMillis) {
        this.properties = properties;
        this.delayMillis = Math.max(0L, delayMillis);
    }

    protected abstract List<LocalDocument> documents();

    protected long artificialDelayMillis() {
        return delayMillis;
    }

    @Override
    public List<SearchHit> search(String query, CancellationToken cancellationToken) throws Exception {
        sleepResponsively(artificialDelayMillis(), cancellationToken);

        List<String> terms = tokenize(query);
        List<SearchHit> hits = new ArrayList<>();
        for (LocalDocument document : documents()) {
            cancellationToken.throwIfCancelled();
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("source " + name() + " interrupted");
            }
            if (terms.isEmpty()) {
                continue;
            }
            double score = scoreDocument(document, terms);
            if (score <= 0.0d) {
                continue;
            }
            Map<String, String> attributes =
                    document.attributes() == null ? Map.of() : Map.copyOf(document.attributes());
            hits.add(new SearchHit(
                    document.id(),
                    name(),
                    document.title(),
                    document.content(),
                    document.url(),
                    score,
                    score,
                    score,
                    null,
                    null,
                    attributes,
                    document.indexedAt(),
                    digest(document.content())));
        }
        hits.sort((left, right) -> Double.compare(right.rawScore(), left.rawScore()));
        return hits;
    }

    @Override
    public boolean enabled() {
        return properties.sources().stream()
                .filter(props -> name().equals(props.name()))
                .map(SearchProperties.SourceProps::enabled)
                .findFirst()
                .orElse(true);
    }

    /**
     * Sleeps in small slices so both thread interruption (timeout / global
     * cancel) and the cooperative token abort the source promptly without
     * leaving the worker blocked until the full simulated delay elapses.
     */
    protected void sleepResponsively(long millis, CancellationToken token) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (millis > 0L) {
            if (token.isCancelled()) {
                throw new CancelledException("source " + name() + " cancelled");
            }
            long slice = Math.min(50L, millis);
            Thread.sleep(slice);
            millis = deadline - System.currentTimeMillis();
        }
    }

    private static double scoreDocument(LocalDocument document, List<String> terms) {
        String title = document.title() == null ? "" : document.title().toLowerCase(Locale.ROOT);
        String content = document.content() == null ? "" : document.content().toLowerCase(Locale.ROOT);
        String url = document.url() == null ? "" : document.url().toLowerCase(Locale.ROOT);
        double score = 0.0d;
        for (String term : terms) {
            score += 3.0d * countOccurrences(title, term);
            score += countOccurrences(content, term);
            score += 2.0d * countOccurrences(url, term);
        }
        return score;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        int pos;
        while ((pos = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from = pos + needle.length();
        }
        return count;
    }

    private static List<String> tokenize(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null) {
            return terms;
        }
        for (String raw : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")) {
            if (!raw.isEmpty() && !terms.contains(raw)) {
                terms.add(raw);
            }
        }
        return terms;
    }

    private static String digest(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
