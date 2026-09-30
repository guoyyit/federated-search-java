package com.github.guoyyit.federatedsearchjava.local;

import com.github.guoyyit.federatedsearchjava.config.SearchProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Wiki/knowledge-base index (local). Freshness: oldest. Deliberately reuses
 * an article document id ({@code doc-federated-search}) and mirrors one
 * article body verbatim, so DOCUMENT_ID and CONTENT_HASH de-duplication are
 * both observable against a single running service.
 */
@Component
public class WikiIndexSource extends AbstractLocalIndexSource {

    private static final long DAY = 86_400_000L;

    private final List<LocalDocument> corpus;

    public WikiIndexSource(SearchProperties properties) {
        super(properties, Long.getLong("federated.source.wiki.delay-ms", 0L));
        long now = System.currentTimeMillis();
        this.corpus = List.of(
                new LocalDocument(
                        "doc-federated-search",
                        "Federated search",
                        "A federated search fans a query out to many independent index sources, "
                                + "collects their hits concurrently and merges them into one ranked list. "
                                + "Slow or failing sources must degrade the result rather than fail the query.",
                        "https://wiki.example.local/Federated_search",
                        Map.of("editor", "wiki-bot"),
                        now - 30 * DAY),
                new LocalDocument(
                        "wiki-score-normalization",
                        "Score normalization",
                        "Min-max normalization rescales every index source's scores to the unit "
                                + "interval. The merged ranking then combines normalized scores with "
                                + "per-source weights configured at runtime.",
                        "https://wiki.example.local/Score_normalization",
                        Map.of(),
                        now - 60 * DAY),
                new LocalDocument(
                        "wiki-pagination-snapshot",
                        "Pagination snapshot",
                        "Stable pagination keeps the merged hit list in a snapshot so changes inside "
                                + "the index sources cannot duplicate or drop documents across pages.",
                        "https://wiki.example.local/Pagination_snapshot",
                        Map.of(),
                        now - 90 * DAY));
    }

    @Override
    public String name() {
        return "wiki";
    }

    @Override
    protected List<LocalDocument> documents() {
        return corpus;
    }
}
