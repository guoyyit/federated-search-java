package com.github.guoyyit.federatedsearchjava.engine;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Query-level cancellation token shared by a coordinator and all sub-queries.
 * Sources poll {@link #isCancelled()} and the coordinator also interrupts
 * worker threads, giving both cooperative and preemptive cancellation.
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** Atomically moves the token to the cancelled state. */
    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Cooperative checkpoint used by local sources while scanning. */
    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw new CancelledException("query cancelled");
        }
    }
}
