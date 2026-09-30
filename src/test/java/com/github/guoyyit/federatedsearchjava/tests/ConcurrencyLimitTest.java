package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.domain.SearchRequest;
import com.github.guoyyit.federatedsearchjava.domain.SearchResponse;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.engine.DedupMode;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coordinator's fixed worker pool is the global bound on sub-queries
 * running at the same instant across all concurrent requests.
 */
class ConcurrencyLimitTest {

    @Test
    void subQueriesNeverExceedConfiguredGlobalLimit() throws Exception {
        int limit = 2;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch hold = new CountDownLatch(1);

        List<IndexSource> sources = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final String name = "s" + i;
            sources.add(new GaugeSource(name, inFlight, peak, hold));
        }
        SearchCoordinator coordinator =
                CoordinatorTestSupport.coordinator(sources, limit);

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            List<Future<SearchResponse>> futures = new ArrayList<>();
            SearchRequest request = new SearchRequest(
                    "federated search", 1, 10, DedupMode.DOCUMENT_ID, 5_000L, null);
            for (int i = 0; i < 2; i++) {
                futures.add(callers.submit(() ->
                        coordinator.search(request, new CancellationToken())));
            }

            // Give the pool time to reach steady state, then release sources.
            Thread.sleep(150L);
            hold.countDown();

            for (Future<SearchResponse> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertTrue(peak.get() <= limit,
                    "peak in-flight sub-queries " + peak.get() + " exceeded limit " + limit);
        } finally {
            callers.shutdownNow();
            coordinator.shutdown();
        }
    }

    private static final class GaugeSource implements IndexSource {

        private final String name;
        private final AtomicInteger inFlight;
        private final AtomicInteger peak;
        private final CountDownLatch hold;

        GaugeSource(String name, AtomicInteger inFlight, AtomicInteger peak, CountDownLatch hold) {
            this.name = name;
            this.inFlight = inFlight;
            this.peak = peak;
            this.hold = hold;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public List<SearchHit> search(String query, CancellationToken cancellationToken)
                throws InterruptedException {
            int current = inFlight.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            try {
                hold.await(2, TimeUnit.SECONDS);
                return List.of();
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }
}
