package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;
import com.github.guoyyit.federatedsearchjava.federation.SourceDispatcher;
import com.github.guoyyit.federatedsearchjava.federation.SourceInvocation;
import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import com.github.guoyyit.federatedsearchjava.source.SearchIndexSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyCapIT {

    @Test
    void inFlightSourceCallsNeverExceedGlobalConcurrency() throws Exception {
        FederatedSearchProperties properties = new FederatedSearchProperties();
        properties.setGlobalConcurrency(3);
        for (int i = 0; i < 8; i++) {
            FederatedSearchProperties.SourceConfig config = new FederatedSearchProperties.SourceConfig();
            config.setTimeout(Duration.ofSeconds(10));
            properties.getSources().put("s" + i, config);
        }

        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        CountDownLatch block = new CountDownLatch(1);
        List<SearchIndexSource> sources = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            sources.add(new GatedSource("s" + i, active, maxObserved, block));
        }

        SourceDispatcher dispatcher = new SourceDispatcher(properties);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> requests = new ArrayList<>();
            for (int q = 0; q < 4; q++) {
                requests.add(pool.submit(() -> {
                    try {
                        SourceInvocation invocation = dispatcher.invoke(sources, "keyboard");
                        invocation.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            Thread.sleep(500);
            assertTrue(maxObserved.get() <= 3,
                    "observed " + maxObserved.get() + " concurrent source calls, cap is 3");
            assertTrue(maxObserved.get() >= 3,
                    "expected the pool to saturate at 3, observed " + maxObserved.get());
            block.countDown();
            for (Future<?> request : requests) {
                request.get(10, TimeUnit.SECONDS);
            }
        } finally {
            block.countDown();
            pool.shutdownNow();
            dispatcher.shutdown();
        }
    }

    private static final class GatedSource implements SearchIndexSource {

        private final String id;
        private final AtomicInteger active;
        private final AtomicInteger maxObserved;
        private final CountDownLatch block;

        private GatedSource(String id, AtomicInteger active, AtomicInteger maxObserved,
                            CountDownLatch block) {
            this.id = id;
            this.active = active;
            this.maxObserved = maxObserved;
            this.block = block;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public List<SourceHit> search(String query) throws InterruptedException {
            int current = active.incrementAndGet();
            maxObserved.accumulateAndGet(current, Math::max);
            try {
                block.await(5, TimeUnit.SECONDS);
            } finally {
                active.decrementAndGet();
            }
            return List.of(new SourceHit(id, id + "-1", "keyboard", "keyboard body",
                    1.0, Instant.EPOCH));
        }
    }
}
