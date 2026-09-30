package com.github.guoyyit.federatedsearchjava.engine;

/**
 * Raised at a cooperative cancellation checkpoint.
 */
public class CancelledException extends RuntimeException {

    public CancelledException(String message) {
        super(message);
    }
}
