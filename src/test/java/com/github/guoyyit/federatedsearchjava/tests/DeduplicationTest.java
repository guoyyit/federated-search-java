package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.engine.DedupMode;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-source merge rules: configurable key, highest-weighted winner and an
 * ordering that does not depend on source registration or arrival order.
 */
class DeduplicationTest {

    private static SearchRequest request(DedupMode mode) {
        return new SearchRequest("federated search", 1, 50, mode, 500L, null);
    }

    @Test
    void duplicatesMergeByDocumentIdAndHigherWeightedScoreWins() {
        SearchHit fromA = CoordinatorTestSupport.hit("a", "dup", 9.0d);
        SearchHit fromB = CoordinatorTestSupport.hit("b", "dup", 1.0d);

        SearchCoordinator firstOrder = CoordinatorTestSupport.coordinator(
                List.of(
                        new FakeIndexSource("a", List.of(fromA), 0L),
                        new FakeIndexSource("b", List.of(fromB), 0L)),
                4,
                Map.of("a", 1.0d, "b", 2.0d));
        SearchCoordinator secondOrder = CoordinatorTestSupport.coordinator(
                List.of(
                        new FakeIndexSource("b", List.of(fromB), 0L),
                        new FakeIndexSource("a", List.of(fromA), 0L)),
                4,
                Map.of("a", 1.0d, "b", 2.0d));

        SearchResponse first = firstOrder.search(request(DedupMode.DOCUMENT_ID), new CancellationToken());
        SearchResponse second = secondOrder.search(request(DedupMode.DOCUMENT_ID), new CancellationToken());

        assertEquals(1, first.totalHits());
        assertEquals("b", first.hits().get(0).source(), "heavier-weight source must win");
        assertEquals(2.0d, first.hits().get(0).finalScore(), 1e-9);
        assertEquals(
                first.hits().get(0).documentId() + ":" + first.hits().get(0).source(),
                second.hits().get(0).documentId() + ":" + second.hits().get(0).source());

        firstOrder.shutdown();
        secondOrder.shutdown();
    }

    @Test
    void sameIdDifferentUrlDoesNotMergeUnderUrlMode() {
        SearchHit a = CoordinatorTestSupport.hit("a", "same", "t", "federated search body",
        "https://a.local/x", 1.0d, "hash-a");
        SearchHit b = CoordinatorTestSupport.hit("b", "same", "t", "federated search body",
        "https://b.local/y", 1.0d, "hash-b");

        SearchCoordinator coordinator = CoordinatorTestSupport.coordinator(
                List.of(
                        new FakeIndexSource("a", List.of(a), 0L),
                        new FakeIndexSource("b", List.of(b), 0L)),
                4,
                Map.of());

        SearchResponse response = coordinator.search(request(DedupMode.URL), new CancellationToken());

        assertEquals(2, response.totalHits(), "different URLs stay separate in URL mode");
        coordinator.shutdown();
    }

    @Test
    void differentIdSameUrlMergesUnderUrlMode() {
        SearchHit a = CoordinatorTestSupport.hit("a", "id-a", "t", "federated search body",
        "https://shared.local/p", 1.0d, "hash-a");
        SearchHit b = CoordinatorTestSupport.hit("b", "id-b", "t", "federated search body",
        "https://shared.local/p", 1.0d, "hash-b");

        SearchCoordinator coordinator = CoordinatorTestSupport.coordinator(
                List.of(
                        new FakeIndexSource("a", List.of(a), 0L),
                        new FakeIndexSource("b", List.of(b), 0L)),
                4,
                Map.of());

        SearchResponse response = coordinator.search(request(DedupMode.URL), new CancellationToken());

        assertEquals(1, response.totalHits());
        coordinator.shutdown();
    }

    @Test
    void differentIdAndUrlMergeUnderContentHashMode() {
        SearchHit a = CoordinatorTestSupport.hit("a", "id-a", "t", "federated search body",
        "https://a.local/x", 1.0d, "shared-content");
        SearchHit b = CoordinatorTestSupport.hit("b", "id-b", "t", "federated search body",
        "https://b.local/y", 1.0d, "shared-content");

        SearchCoordinator coordinator = CoordinatorTestSupport.coordinator(
                List.of(
                        new FakeIndexSource("a", List.of(a), 0L),
                        new FakeIndexSource("b", List.of(b), 0L)),
                4,
                Map.of());

        SearchResponse response = coordinator.search(request(DedupMode.CONTENT_HASH), new CancellationToken());

        assertEquals(1, response.totalHits(), "identical content collapses in CONTENT_HASH mode");
        coordinator.shutdown();
    }

    @Test
    void rankingIsIndependentOfSourceOrder() {
        SearchHit a = CoordinatorTestSupport.hit("a", "doc-a", 1.0d);
        SearchHit b = CoordinatorTestSupport.hit("b", "doc-b", 1.0d);

        SearchCoordinator firstOrder = CoordinatorTestSupport.coordinator(
                List.of(
                        new FakeIndexSource("a", List.of(a), 0L),
                        new FakeIndexSource("b", List.of(b), 30L)),
                4,
                Map.of("a", 1.0d, "b", 2.0d));
        SearchCoordinator secondOrder = CoordinatorTestSupport.coordinator(
                List.of(
                        new FakeIndexSource("b", List.of(b), 30L),
                        new FakeIndexSource("a", List.of(a), 0L)),
                4,
                Map.of("a", 1.0d, "b", 2.0d));

        SearchResponse first = firstOrder.search(request(DedupMode.DOCUMENT_ID), new CancellationToken());
        SearchResponse second = secondOrder.search(request(DedupMode.DOCUMENT_ID), new CancellationToken());

        assertEquals(List.of("doc-b", "doc-a"), ids(first));
        assertEquals(List.of("doc-b", "doc-a"), ids(second));

        firstOrder.shutdown();
        secondOrder.shutdown();
    }

    private static List<String> ids(SearchResponse response) {
        return response.hits().stream().map(SearchHit::documentId).toList();
    }
}
