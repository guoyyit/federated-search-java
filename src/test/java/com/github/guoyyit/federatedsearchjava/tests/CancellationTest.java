package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.domain.SourceState;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.engine.DedupMode;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancelling the query token stops every dispatched sub-query quickly and
 * leaves no background work running.
 */
class CancellationTest {

    @Test
    void cancelAbortsAllDispatchedSubQueries() throws Exception {
        FakeIndexSource slowA = new FakeIndexSource("a", List.of(), 5_000L);
        FakeIndexSource slowB = new FakeIndexSource("b", List.of(), 5_000L);
        FakeIndexSource slowC = new FakeIndexSource("c", List.of(), 5_000L);

        SearchCoordinator coordinator =
                CoordinatorTestSupport.coordinator(List.of(slowA, slowB, slowC), 4);
        CancellationToken token = new CancellationToken();
        SearchRequest request =
                new SearchRequest("federated search", 1, 10,
                        DedupMode.DOCUMENT_ID, 10_000L, null);

        SearchResponse[] responseHolder = new SearchResponse[1];
        Thread caller = new Thread(
                () -> responseHolder[0] = coordinator.search(request, token), "test-caller");
        long started = System.currentTimeMillis();
        caller.start();

        // Let all three sub-queries start, then cancel.
        Thread.sleep(80L);
        assertTrue(token.cancel());
        caller.join(2_000L);

        long elapsed = System.currentTimeMillis() - started;
        assertTrue(!caller.isAlive(), "cancelled search must return promptly");
        assertTrue(elapsed < 2_000L, "cancellation must not wait for source timeout, took " + elapsed + "ms");

        // Workers respond to interruption, so nothing keeps scanning in
        // the background after the caller gave up.
        assertTrue(slowA.wasStopped());
        assertTrue(slowB.wasStopped());
        assertTrue(slowC.wasStopped());

        assertTrue(responseHolder[0] != null);
        assertTrue(responseHolder[0].sourceReports().stream()
                .allMatch(report -> report.state() == SourceState.CANCELLED));

        coordinator.shutdown();
    }
}
