package com.github.guoyyit.federatedsearchjava.model;

public record SearchRequest(
        String query,
        int page,
        int pageSize,
        String snapshotId
) {
}
