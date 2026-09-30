package com.github.guoyyit.federatedsearchjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * All configuration knobs for federated search, bound from
 * the {@code federated.search.*} prefix.
 */
@ConfigurationProperties(prefix = "federated.search")
public record SearchProperties(
        Long defaultTimeoutMs,
        Integer maxConcurrentRequests,
        Long snapshotTtlSeconds,
        String dedupBy,
        Integer snippetLength,
        String highlightOpen,
        String highlightClose,
        List<SourceProps> sources
) {

    public SearchProperties {
        if (sources == null) {
            sources = List.of();
        }
    }

    public record SourceProps(
            String name,
            Boolean enabled,
            Double weight,
            Long timeoutMs
    ) {
    }
}
