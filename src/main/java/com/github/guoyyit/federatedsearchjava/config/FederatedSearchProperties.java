package com.github.guoyyit.federatedsearchjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "federated.search")
public class FederatedSearchProperties {

    private int globalConcurrency = 8;
    private DedupStrategy dedupStrategy = DedupStrategy.DOC_ID;
    private int snippetLength = 160;
    private String highlightPrefix = "[";
    private String highlightSuffix = "]";
    private Duration snapshotTtl = Duration.ofMinutes(10);
    private int snapshotMax = 100;
    private Map<String, SourceConfig> sources = new LinkedHashMap<>();

    public int getGlobalConcurrency() {
        return globalConcurrency;
    }

    public void setGlobalConcurrency(int globalConcurrency) {
        this.globalConcurrency = globalConcurrency;
    }

    public DedupStrategy getDedupStrategy() {
        return dedupStrategy;
    }

    public void setDedupStrategy(DedupStrategy dedupStrategy) {
        this.dedupStrategy = dedupStrategy;
    }

    public int getSnippetLength() {
        return snippetLength;
    }

    public void setSnippetLength(int snippetLength) {
        this.snippetLength = snippetLength;
    }

    public String getHighlightPrefix() {
        return highlightPrefix;
    }

    public void setHighlightPrefix(String highlightPrefix) {
        this.highlightPrefix = highlightPrefix;
    }

    public String getHighlightSuffix() {
        return highlightSuffix;
    }

    public void setHighlightSuffix(String highlightSuffix) {
        this.highlightSuffix = highlightSuffix;
    }

    public Duration getSnapshotTtl() {
        return snapshotTtl;
    }

    public void setSnapshotTtl(Duration snapshotTtl) {
        this.snapshotTtl = snapshotTtl;
    }

    public int getSnapshotMax() {
        return snapshotMax;
    }

    public void setSnapshotMax(int snapshotMax) {
        this.snapshotMax = snapshotMax;
    }

    public Map<String, SourceConfig> getSources() {
        return sources;
    }

    public void setSources(Map<String, SourceConfig> sources) {
        this.sources = sources;
    }

    public static class SourceConfig {

        private Duration timeout = Duration.ofSeconds(2);
        private double weight = 1.0;

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }
    }
}
