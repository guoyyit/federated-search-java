package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SourceWeightRegistry {

    private final FederatedSearchProperties properties;
    private final ConcurrentHashMap<String, Double> overrides = new ConcurrentHashMap<>();

    public SourceWeightRegistry(FederatedSearchProperties properties) {
        this.properties = properties;
    }

    public double getWeight(String sourceId) {
        Double override = overrides.get(sourceId);
        if (override != null) {
            return override;
        }
        FederatedSearchProperties.SourceConfig config = properties.getSources().get(sourceId);
        return config == null ? 1.0 : config.getWeight();
    }

    public void setWeight(String sourceId, double weight) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (Double.isNaN(weight) || Double.isInfinite(weight) || weight < 0) {
            throw new IllegalArgumentException("weight must be a finite non-negative number");
        }
        overrides.put(sourceId, weight);
    }
}
