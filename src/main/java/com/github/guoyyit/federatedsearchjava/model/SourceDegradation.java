package com.github.guoyyit.federatedsearchjava.model;

public record SourceDegradation(
        String sourceId,
        DegradationReason reason,
        String detail
) {
}
