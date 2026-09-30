package com.github.guoyyit.federatedsearchjava.engine;

/**
 * Cross-source de-duplication key strategy.
 */
public enum DedupMode {
    DOCUMENT_ID,
    CONTENT_HASH,
    URL
}
