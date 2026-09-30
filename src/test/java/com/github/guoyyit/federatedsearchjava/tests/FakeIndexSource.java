package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Configurable local source for coordinator tests: canned hits, delay,
 * failure and an observable "stopped" flag that flips when the worker is
 * interrupted (cancellation / timeout).
 */
final class FakeIndexSource implements IndexSource {

    private final String name;
    private final Supplier<List<SearchHit>> hitsSupplier;
    private final long delayMillis;
    private final RuntimeException failure;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    FakeIndexSource(String name, List<SearchHit> hits, long delayMillis) {
        this(name, () -> hits, delayMillis, null);
    }

    FakeIndexSource(
            String name,
            Supplier<List<SearchHit>> hitsSupplier,
            long delayMillis,
            RuntimeException failure) {
        this.name = name;
        this.hitsSupplier = hitsSupplier;
        this.delayMillis = delayMillis;
        this.failure = failure;
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
    public List<SearchHit> search(String query, CancellationToken cancellationToken) throws Exception {
        try {
            long remaining = delayMillis;
            while (remaining > 0L) {
                if (cancellationToken.isCancelled()) {
                    throw new InterruptedException("cancelled via token");
                }
                long slice = Math.min(10L, remaining);
                Thread.sleep(slice);
                remaining -= slice;
            }
            if (failure != null) {
                throw failure;
            }
            return hitsSupplier.get();
        } catch (InterruptedException e) {
            stopped.set(true);
            throw e;
        }
    }

    boolean wasStopped() {
        return stopped.get();
    }
}
