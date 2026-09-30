package com.github.guoyyit.federatedsearchjava.local;

import com.github.guoyyit.federatedsearchjava.config.SearchProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Product catalog index (local). Freshness: medium. Schema differs from the
 * article/wiki sources: some documents miss title or url, exercising the
 * merge layer's fallback behaviour.
 */
@Component
public class ProductIndexSource extends AbstractLocalIndexSource {

    private static final long DAY = 86_400_000L;

    private final List<LocalDocument> corpus;

    public ProductIndexSource(SearchProperties properties) {
        super(properties, Long.getLong("federated.source.products.delay-ms", 0L));
        long now = System.currentTimeMillis();
        this.corpus = List.of(
                new LocalDocument(
                        "sku-search-appliance",
                        "Enterprise Search Appliance",
                        "Hardware appliance for federated search across on-premise index sources, "
                                + "with per-source timeout policies and bounded concurrency.",
                        "https://shop.example.local/p/search-appliance",
                        Map.of("price", "4999", "currency", "USD"),
                        now - 2 * DAY),
                new LocalDocument(
                        "sku-result-highlighter",
                        null,
                        "Snippet and highlight toolkit for search result rendering: safe truncation, "
                                + "missing-field fallbacks and deterministic output for any query.",
                        "https://shop.example.local/p/highlighter",
                        Map.of("price", "29"),
                        now - 7 * DAY),
                new LocalDocument(
                        "sku-no-url-doc",
                        "Legacy Ranking Module",
                        "A ranking module that normalizes native index scores into a common scale and "
                                + "combines them with configurable source weights.",
                        null,
                        Map.of("price", "99"),
                        now - 20 * DAY));
    }

    @Override
    public String name() {
        return "products";
    }

    @Override
    protected List<LocalDocument> documents() {
        return corpus;
    }
}
