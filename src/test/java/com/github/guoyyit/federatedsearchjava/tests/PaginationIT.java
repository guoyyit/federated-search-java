package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.federation.SnapshotExpiredException;
import com.github.guoyyit.federatedsearchjava.model.SearchHit;
import com.github.guoyyit.federatedsearchjava.model.SearchRequest;
import com.github.guoyyit.federatedsearchjava.model.SearchResponse;
import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginationIT {

    private TestContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.shutdown();
        }
    }

    @Test
    void pagesOverSnapshotHaveNoDuplicatesOrGaps() throws Exception {
        context = new TestContext(List.of(ControllableSource.fixed("a", seedHits(12))));

        SearchResponse first = context.service
                .search(new SearchRequest("keyboard", 0, 5, null));
        assertEquals(12, first.totalHits());

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            SearchResponse response = context.service.search(
                    new SearchRequest("keyboard", page, 5, first.snapshotId()));
            assertEquals(page == 2 ? 2 : 5, response.hits().size());
            for (SearchHit hit : response.hits()) {
                assertFalse(seen.contains(hit.docId()), "duplicate across pages: " + hit.docId());
                seen.add(hit.docId());
            }
        }
        assertEquals(12, seen.size());
    }

    @Test
    void unknownSnapshotIsRejectedWithClearError() {
        context = new TestContext(List.of(ControllableSource.of("a")));
        assertThrows(SnapshotExpiredException.class, () -> context.service
                .search(new SearchRequest("keyboard", 1, 5, "missing-snapshot")));
    }

    @Test
    void outOfRangePageReturnsEmptyHitsButKeepsTotal() throws Exception {
        context = new TestContext(List.of(ControllableSource.fixed("a", seedHits(3))));
        SearchResponse first = context.service
                .search(new SearchRequest("keyboard", 0, 5, null));
        SearchResponse tail = context.service.search(
                new SearchRequest("keyboard", 5, 5, first.snapshotId()));
        assertEquals(3, tail.totalHits());
        assertEquals(0, tail.hits().size());
    }

    @Test
    void concurrentPagingStaysStableWhileSourceDataChanges() throws Exception {
        MutableSource mutable = new MutableSource("a");
        mutable.setDocs(seedHits(10));
        context = new TestContext(List.of(mutable));

        SearchResponse first = context.service
                .search(new SearchRequest("keyboard", 0, 4, null));
        String snapshotId = first.snapshotId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        ConcurrentLinkedQueue<String> allSeen = new ConcurrentLinkedQueue<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> {
                for (int change = 0; change < 20; change++) {
                    mutable.setDocs(seedHits(10 + change % 7));
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }));
            for (int page = 0; page < 3; page++) {
                final int pageNumber = page;
                futures.add(pool.submit(() -> {
                    try {
                        SearchResponse response = context.service.search(
                                new SearchRequest("keyboard", pageNumber, 4, snapshotId));
                        assertEquals(10, response.totalHits());
                        for (SearchHit hit : response.hits()) {
                            allSeen.add(hit.docId());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<String> unique = allSeen.stream().distinct().sorted().toList();
        assertEquals(10, unique.size(),
                "snapshot pages must return exactly the original 10 docs, got " + allSeen);
        for (int i = 0; i < 10; i++) {
            assertEquals("doc-" + i, unique.get(i));
        }
    }

    private List<SourceHit> seedHits(int count) {
        List<SourceHit> hits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hits.add(ControllableSource.hit("a", "doc-" + i,
                    "keyboard article number " + i, count - i));
        }
        return hits;
    }
}
