package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.config.SearchProperties;
import com.github.guoyyit.federatedsearchjava.config.WeightRegistry;
import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.engine.Deduplicator;
import com.github.guoyyit.federatedsearchjava.engine.DedupMode;
import com.github.guoyyit.federatedsearchjava.engine.Highlighter;
import com.github.guoyyit.federatedsearchjava.engine.ScoreNormalizer;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import com.github.guoyyit.federatedsearchjava.engine.SnapshotStore;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runtime weight changes apply to the next request while the same coordinator
 * (and its sources/indexes) stays alive.
 */
class WeightAdjustmentTest {

    @Test
    void newWeightAffectsNextQueryWithoutRebuild() {
        SearchProperties properties = new SearchProperties(
                500L, 4, 60L, "DOCUMENT_ID", 160, "<em>", "</em>", List.of());
        WeightRegistry registry = new WeightRegistry(properties);
        List<IndexSource> sources = List.of(
                new FakeIndexSource("a", List.of(CoordinatorTestSupport.hit("a", "doc-a", 1.0d)), 0L),
                new FakeIndexSource("b", List.of(CoordinatorTestSupport.hit("b", "doc-b", 1.0d)), 0L));
        SearchCoordinator coordinator = new SearchCoordinator(
                sources,
                properties,
                registry,
                new ScoreNormalizer(),
                new Deduplicator(),
                new Highlighter(160, "<em>", "</em>"),
                new SnapshotStore(60L));
        SearchRequest request =
                new SearchRequest("federated search", 1, 10, DedupMode.DOCUMENT_ID, 500L, null);

        SearchResponse before = coordinator.search(request, new CancellationToken());
        assertEquals(List.of("doc-a", "doc-b"), ids(before), "equal weights -> source-name tie-break");

        registry.updateWeight("b", 5.0d);

        SearchResponse after = coordinator.search(request, new CancellationToken());
        assertEquals(List.of("doc-b", "doc-a"), ids(after), "updated weight reorders immediately");
        SearchHit winner = after.hits().get(0);
        assertEquals("b", winner.source());
        assertEquals(5.0d, winner.finalScore(), 1e-9, "finalScore = normalized(=1.0) * weight");

        coordinator.shutdown();
    }

    private static List<String> ids(SearchResponse response) {
        return response.hits().stream().map(SearchHit::documentId).toList();
    }
}
