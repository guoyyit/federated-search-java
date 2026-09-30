package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.federation.SearchTask;
import com.github.guoyyit.federatedsearchjava.model.DegradationReason;
import com.github.guoyyit.federatedsearchjava.model.SearchRequest;
import com.github.guoyyit.federatedsearchjava.model.SearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationIT {

    private TestContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.shutdown();
        }
    }

    @Test
    void cancellingTaskInterruptsAllInflightSources() throws Exception {
        ControllableSource slowA = ControllableSource.slow("a", 5_000);
        ControllableSource slowB = ControllableSource.slow("b", 5_000);
        context = new TestContext(List.of(slowA, slowB));

        SearchTask task = context.service.submit(new SearchRequest("keyboard", 0, 10, null));
        Thread.sleep(150);
        assertTrue(task.cancel());

        SearchResponse response = task.await(Duration.ofSeconds(2));
        assertFalse(response.complete());
        assertEquals(2, response.degradations().size());
        assertTrue(response.degradations().stream()
                .allMatch(deg -> deg.reason() == DegradationReason.CANCELLED));

        long deadline = System.currentTimeMillis() + 2_000;
        while ((!slowA.wasInterrupted() || !slowB.wasInterrupted())
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(slowA.wasInterrupted(), "source A task must be interrupted");
        assertTrue(slowB.wasInterrupted(), "source B task must be interrupted");
    }

    @Test
    void requestLevelTimeoutCancelsOutstandingSourcesAndReturnsPartialResult() throws Exception {
        ControllableSource fast = ControllableSource.of("a",
                ControllableSource.hit("a", "a-1", "keyboard", 5.0));
        ControllableSource slow = ControllableSource.slow("b", 5_000);
        context = new TestContext(List.of(fast, slow));

        long start = System.currentTimeMillis();
        SearchResponse response = context.service
                .submit(new SearchRequest("keyboard", 0, 10, null))
                .await(Duration.ofMillis(300));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 1_500, "await returned after " + elapsed + "ms");
        assertEquals(1, response.totalHits());
        assertFalse(response.complete());
        assertTrue(response.degradations().stream()
                .anyMatch(deg -> deg.sourceId().equals("b")
                        && (deg.reason() == DegradationReason.CANCELLED
                        || deg.reason() == DegradationReason.TIMEOUT)));

        long deadline = System.currentTimeMillis() + 2_000;
        while (!slow.wasInterrupted() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(slow.wasInterrupted(), "timed-out request must abort the slow source");
    }
}
