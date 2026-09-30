package com.github.guoyyit.federatedsearchjava.local;

import com.github.guoyyit.federatedsearchjava.config.SearchProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Article index (local). Freshness: newest corpus of the three. Shares
 * document ids with the wiki source so cross-source de-duplication is
 * observable. Disabled via
 * {@code federated.search.sources.articles.enabled=false}.
 */
@Component
public class ArticleIndexSource extends AbstractLocalIndexSource {

    private static final long DAY = 86_400_000L;

    private final List<LocalDocument> corpus;

    public ArticleIndexSource(SearchProperties properties) {
        super(properties, delayMillis());
        long now = System.currentTimeMillis();
        this.corpus = List.of(
                new LocalDocument(
                        "doc-federated-search",
                        "Federated Search at Scale",
                        "A federated search fans a query out to many independent index sources, "
                                + "collects their hits concurrently and merges them into one ranked list. "
                                + "Slow or failing sources must degrade the result rather than fail the query.",
                        "https://example.local/articles/federated-search",
                        Map.of("author", "A. Rivera", "section", "engineering"),
                        now - DAY),
                new LocalDocument(
                        "doc-query-timeouts",
                        "Handling Slow Index Sources",
                        "Every index source gets its own timeout. A timed-out source is reported as a "
                                + "degradation while the remaining sources still produce a ranked answer.",
                        "https://example.local/articles/slow-sources",
                        Map.of("author", "M. Chen"),
                        now - 3 * DAY),
                new LocalDocument(
                        "doc-result-ranking",
                        "Merging and Ranking Cross-Source Hits",
                        "Native scores from different sources are not comparable. Normalize each source "
                                + "independently, apply a source weight, then sort with a deterministic tie-break.",
                        "https://example.local/articles/merge-ranking",
                        Map.of("author", "S. Park"),
                        now - 5 * DAY));
    }

    @Override
    public String name() {
        return "articles";
    }

    @Override
    protected List<LocalDocument> documents() {
        return corpus;
    }

    private static long delayMillis() {
        return Long.getLong("federated.source.articles.delay-ms", 0L);
    }
}
