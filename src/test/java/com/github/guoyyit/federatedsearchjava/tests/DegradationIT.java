package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.model.DegradationReason;
import com.github.guoyyit.federatedsearchjava.model.SearchRequest;
import com.github.guoyyit.federatedsearchjava.model.SearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DegradationIT {

    private TestContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.shutdown();
        }
    }

    @Test
    void slowSourceTimesOutButOverallQuerySucceeds() throws Exception {
        ControllableSource fast = ControllableSource.of("a",
                ControllableSource.hit("a", "a-1", "keyboard guide", 5.0));
        ControllableSource slow = ControllableSource.slow("b", 2_000,
                ControllableSource.hit("b", "b-1", "late document", 1.0));
        context = new TestContext(List.of(fast, slow));

        long start = System.currentTimeMillis();
        SearchResponse response = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(response.complete(), "response must be flagged as degraded");
        assertEquals(1, response.degradations().size());
        assertEquals("b", response.degradations().get(0).sourceId());
        assertEquals(DegradationReason.TIMEOUT, response.degradations().get(0).reason());
        assertEquals(1, response.totalHits());
        assertEquals("a-1", response.hits().get(0).docId());
        assertTrue(elapsed < 1_000, "overall query must not wait for the slow source, took " + elapsed + "ms");
        assertTrue(slow.wasInterrupted(), "timed-out sub-query must be interrupted");
    }

    @Test
    void failingSourceIsReportedAndOtherSourcesStillReturned() throws Exception {
        ControllableSource good = ControllableSource.of("a",
                ControllableSource.hit("a", "a-1", "keyboard", 4.0));
        ControllableSource broken = ControllableSource.failing("b",
                new IllegalStateException("index corrupted"));
        context = new TestContext(List.of(good, broken));

        SearchResponse response = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));

        assertFalse(response.complete());
        Map<String, DegradationReason> reasons = response.degradations().stream()
                .collect(Collectors.toMap(
                        deg -> deg.sourceId(),
                        deg -> deg.reason()));
        assertEquals(DegradationReason.FAILED, reasons.get("b"));
        assertTrue(response.degradations().get(0).detail().contains("index corrupted"));
        assertEquals(1, response.totalHits());
        assertEquals("a-1", response.hits().get(0).docId());
    }

    @Test
    void allSourcesSucceedMeansResponseIsComplete() throws Exception {
        ControllableSource a = ControllableSource.of("a",
                ControllableSource.hit("a", "a-1", "keyboard", 4.0));
        ControllableSource b = ControllableSource.of("b",
                ControllableSource.hit("b", "b-1", "keyboard news", 2.0));
        context = new TestContext(List.of(a, b));

        SearchResponse response = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));

        assertTrue(response.complete());
        assertTrue(response.degradations().isEmpty());
        assertEquals(2, response.totalHits());
    }

}
