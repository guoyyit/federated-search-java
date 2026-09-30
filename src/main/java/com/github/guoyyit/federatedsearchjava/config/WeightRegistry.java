package com.github.guoyyit.federatedsearchjava.config;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the effective per-source weight at runtime so weight changes take
 * effect on the next request without rebuilding any index.
 */
@Component
public class WeightRegistry {

    private final ConcurrentHashMap<String, Double> weights = new ConcurrentHashMap<>();

    public WeightRegistry(SearchProperties properties) {
        for (SearchProperties.SourceProps source : properties.sources()) {
            if (source.name() != null && source.weight() != null) {
                weights.put(source.name(), source.weight());
            }
        }
    }

    /** Effective weight of a source; defaults to 1.0 when unconfigured. */
    public double weightOf(String source) {
        Double value = weights.get(source);
        return value == null ? 1.0d : value;
    }

    /** Runtime update; the very next query sees the new value. */
    public void updateWeight(String source, double weight) {
        if (weight < 0.0d) {
            throw new IllegalArgumentException("weight must be non-negative: " + weight);
        }
        weights.put(source, weight);
    }

    public Map<String, Double> all() {
        return Collections.unmodifiableMap(new HashMap<>(weights));
    }
}
