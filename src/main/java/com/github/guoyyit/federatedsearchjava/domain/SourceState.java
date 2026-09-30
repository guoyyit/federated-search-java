package com.github.guoyyit.federatedsearchjava.domain;

/**
 * Outcome of dispatching a query to one source. COMPLETE means the source
 * returned in time; every other state is an explainable degradation.
 */
public enum SourceState {
    COMPLETE,
    TIMEOUT,
    FAILED,
    CANCELLED
}
