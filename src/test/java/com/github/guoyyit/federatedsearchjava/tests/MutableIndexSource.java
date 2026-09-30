package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.engine.CancellationToken;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Source whose corpus can change between requests, simulating data freshness
 * drift while a caller pages.
 */
final class MutableIndexSource implements IndexSource {

    private final String name;
    private final List<SearchHit> documents;
    private final long delayMillis;
    private final AtomicInteger invocations = new AtomicInteger();

    MutableIndexSource(String name, List<SearchHit> initialDocuments, long delayMillis) {
        this.name = name;
        this.documents = new ArrayList<>(initialDocuments);
        this.delayMillis = delayMillis;
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
        invocations.incrementAndGet();
        if (delayMillis > 0L) {
            Thread.sleep(delayMillis);
        }
        synchronized (documents) {
            return new ArrayList<>(documents);
        }
    }

    void replaceAll(List<SearchHit> next) {
        synchronized (documents) {
            documents.clear();
            documents.addAll(next);
        }
    }

    int invocations() {
        return invocations.get();
    }
}
