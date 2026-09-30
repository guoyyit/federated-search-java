package com.github.guoyyit.federatedsearchjava.local;

import java.util.Map;

/**
 * Document stored in a local index. Fields may be null: local sources differ
 * in freshness and schema, and the merge layer must tolerate missing fields.
 */
public record LocalDocument(
        String id,
        String title,
        String content,
        String url,
        Map<String, String> attributes,
        long indexedAt
) {
}
