package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import com.github.guoyyit.federatedsearchjava.source.SearchIndexSource;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class ControllableSource implements SearchIndexSource {

    private final String id;
    private final List<SourceHit> hits;
    private final long delayMillis;
    private final RuntimeException failure;
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    private ControllableSource(String id, List<SourceHit> hits, long delayMillis,
                               RuntimeException failure) {
        this.id = id;
        this.hits = hits;
        this.delayMillis = delayMillis;
        this.failure = failure;
    }

    static ControllableSource of(String id, SourceHit... hits) {
        return new ControllableSource(id, List.of(hits), 0L, null);
    }

    static ControllableSource slow(String id, long delayMillis, SourceHit... hits) {
        return new ControllableSource(id, List.of(hits), delayMillis, null);
    }

    static ControllableSource failing(String id, RuntimeException failure) {
        return new ControllableSource(id, List.of(), 0L, failure);
    }

    static ControllableSource fixed(String id, List<SourceHit> hits) {
        return new ControllableSource(id, hits, 0L, null);
    }

    static SourceHit hit(String source, String docId, String title, double score) {
        return new SourceHit(source, docId, title, title + " body for testing matching",
                score, Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<SourceHit> search(String query) throws InterruptedException {
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return hits;
    }

    boolean wasInterrupted() {
        return interrupted.get();
    }
}
