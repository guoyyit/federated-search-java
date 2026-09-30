package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.domain.SourceReport;
import com.github.guoyyit.federatedsearchjava.domain.SourceState;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.engine.DedupMode;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A slow source is reported TIMEOUT and a throwing source FAILED; the healthy
 * source still answers, the overall request never fails.
 */
class SourceDegradationTest {

    @Test
    void timeoutAndFailureDegradeButDoNotFailQuery() {
        FakeIndexSource healthy = new FakeIndexSource(
                "healthy", List.of(CoordinatorTestSupport.hit("healthy", "h1", 5.0d)), 0L);
        FakeIndexSource slow = new FakeIndexSource(
                "slow", List.of(), 500L);
        FakeIndexSource broken = new FakeIndexSource(
                "broken",
                () -> {
                    throw new RuntimeException("index corrupted");
                },
                0L,
                new RuntimeException("index corrupted"));

        SearchCoordinator coordinator =
                CoordinatorTestSupport.coordinator(List.of(healthy, slow, broken), 4);

        SearchResponse response = coordinator.search(
                new SearchRequest("federated search", 1, 10,
                        DedupMode.DOCUMENT_ID, 100L, null),
                new CancellationToken());

        assertFalse(response.complete(), "degraded response must not be complete");
        assertTrue(response.degraded());
        assertEquals(1, response.totalHits());
        assertEquals("h1", response.hits().get(0).documentId());

        SourceReport timeoutReport = report(response, "slow");
        assertEquals(SourceState.TIMEOUT, timeoutReport.state());
        assertNotNull(timeoutReport.reason());
        assertTrue(timeoutReport.reason().contains("100"));

        SourceReport failureReport = report(response, "broken");
        assertEquals(SourceState.FAILED, failureReport.state());
        assertTrue(failureReport.reason().contains("index corrupted"));

        assertEquals(SourceState.COMPLETE, report(response, "healthy").state());

        // The overdue sub-query must actually be interrupted rather than
        // lingering in the background.
        assertTrue(slow.wasStopped(), "timed-out source worker must be interrupted");

        coordinator.shutdown();
    }

    @Test
    void oneSlowSourceCostsOneTimeoutWindowNotTwo() {
        FakeIndexSource healthy = new FakeIndexSource(
                "healthy", List.of(CoordinatorTestSupport.hit("healthy", "h1", 5.0d)), 0L);
        FakeIndexSource slow = new FakeIndexSource("slow", List.of(), 5_000L);
        SearchCoordinator coordinator =
                CoordinatorTestSupport.coordinator(List.of(healthy, slow), 4);

        long started = System.currentTimeMillis();
        SearchResponse response = coordinator.search(
                new SearchRequest("federated search", 1, 10,
                        DedupMode.DOCUMENT_ID, 150L, null),
                new CancellationToken());
        long elapsed = System.currentTimeMillis() - started;

        assertEquals(SourceState.TIMEOUT, report(response, "slow").state());
        assertTrue(elapsed < 300L,
                "query should wait ~one timeout window (150ms), took " + elapsed + "ms");
        coordinator.shutdown();
    }

    private static SourceReport report(SearchResponse response, String source) {
        return response.sourceReports().stream()
                .filter(report -> report.source().equals(source))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing report for " + source));
    }
}
