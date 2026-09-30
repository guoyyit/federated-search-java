package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.engine.DedupMode;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot pagination: source-side changes after page one must not duplicate
 * or drop documents across pages, and later pages must not re-hit sources.
 */
class PaginationTest {

    private static SearchRequest pageRequest(int page, int pageSize, String snapshotId) {
        return new SearchRequest("federated search", page, pageSize,
                DedupMode.DOCUMENT_ID, 500L, snapshotId);
    }

    @Test
    void pagesStayStableWhenSourceDataChanges() {
        List<SearchHit> initial = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String id = String.format("doc-%02d", i);
            initial.add(CoordinatorTestSupport.hit("a", id, 1.0d));
        }
        MutableIndexSource source = new MutableIndexSource("a", initial, 0L);
        SearchCoordinator coordinator =
                CoordinatorTestSupport.coordinator(List.of(source), 4);

        SearchResponse page1 = coordinator.search(
                pageRequest(1, 3, null), new CancellationToken());
        assertEquals(10, page1.totalHits());
        assertEquals(3, page1.hits().size());

        // Source mutates: first five documents disappear, three new ones appear.
        List<SearchHit> changed = new ArrayList<>();
        for (int i = 6; i <= 10; i++) {
            changed.add(CoordinatorTestSupport.hit("a", String.format("doc-%02d", i), 1.0d));
        }
        changed.add(CoordinatorTestSupport.hit("a", "doc-new-1", 9.0d));
        changed.add(CoordinatorTestSupport.hit("a", "doc-new-2", 9.0d));
        changed.add(CoordinatorTestSupport.hit("a", "doc-new-3", 9.0d));
        source.replaceAll(changed);

        List<String> seen = new ArrayList<>();
        seen.addAll(ids(page1));
        for (int page = 2; page <= 4; page++) {
            SearchResponse next = coordinator.search(
                    pageRequest(page, 3, page1.snapshotId()), new CancellationToken());
            assertEquals(10, next.totalHits());
            seen.addAll(ids(next));
        }

        assertEquals(10, seen.size());
        assertEquals(10, new HashSet<>(seen).size(), "no cross-page duplicates");

        Set<String> expected = new HashSet<>();
        for (int i = 1; i <= 10; i++) {
            expected.add(String.format("doc-%02d", i));
        }
        assertEquals(expected, new HashSet<>(seen), "no cross-page misses");

        assertEquals(1, source.invocations(), "later pages read the snapshot, not the sources");
        coordinator.shutdown();
    }

    @Test
    void independentCallersCanPageConcurrentlyWithoutInterference() throws Exception {
        List<SearchHit> corpusA = corpus("a", 12);
        List<SearchHit> corpusB = corpus("b", 12);
        MutableIndexSource sourceA = new MutableIndexSource("a", corpusA, 0L);
        MutableIndexSource sourceB = new MutableIndexSource("b", corpusB, 0L);
        SearchCoordinator coordinator =
                CoordinatorTestSupport.coordinator(List.of(sourceA, sourceB), 8);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int caller = 0; caller < 2; caller++) {
                final int callerId = caller;
                futures.add(pool.submit(() -> {
                    List<String> collected = new ArrayList<>();
                    SearchResponse first = coordinator.search(
                            pageRequest(1, 8, null), new CancellationToken());
                    collected.addAll(ids(first));
                    for (int page = 2; page <= 3; page++) {
                        SearchResponse next = coordinator.search(
                                pageRequest(page, 8, first.snapshotId()), new CancellationToken());
                        collected.addAll(ids(next));
                    }
                    return collected;
                }));
            }

            List<List<String>> results = new ArrayList<>();
            for (Future<List<String>> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }

            // Different sources -> 24 distinct documents in total, and each
            // caller sees a consistent 24-document snapshot without dupes.
            for (List<String> result : results) {
                assertEquals(24, result.size());
                assertEquals(24, new HashSet<>(result).size());
            }
            Set<String> combined = new HashSet<>();
            combined.addAll(results.get(0));
            combined.addAll(results.get(1));
            assertEquals(24, combined.size());
        } finally {
            pool.shutdownNow();
            coordinator.shutdown();
        }
    }

    private static List<SearchHit> corpus(String source, int count) {
        List<SearchHit> documents = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            documents.add(CoordinatorTestSupport.hit(
                    source, source + "-doc-" + i, 1.0d));
        }
        return Collections.unmodifiableList(documents);
    }

    private static List<String> ids(SearchResponse response) {
        List<String> ids = new ArrayList<>();
        response.hits().forEach(hit -> ids.add(hit.documentId()));
        return ids;
    }
}
