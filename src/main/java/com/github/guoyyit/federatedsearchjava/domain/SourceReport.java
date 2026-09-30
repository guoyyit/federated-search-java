package com.github.guoyyit.federatedsearchjava.domain;

/**
 * Per-source dispatch report attached to every response so callers can tell
 * complete results from degraded ones.
 */
public record SourceReport(
        String source,
        SourceState state,
        String reason,
        long elapsedMillis,
        int hitCount
) {
}
