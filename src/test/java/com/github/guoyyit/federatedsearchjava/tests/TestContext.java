package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.config.DedupKeyResolver;
import com.github.guoyyit.federatedsearchjava.config.DedupStrategy;
import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;
import com.github.guoyyit.federatedsearchjava.federation.FederatedSearchService;
import com.github.guoyyit.federatedsearchjava.federation.Highlighter;
import com.github.guoyyit.federatedsearchjava.federation.ResultMerger;
import com.github.guoyyit.federatedsearchjava.federation.ScoreNormalizer;
import com.github.guoyyit.federatedsearchjava.federation.SnapshotStore;
import com.github.guoyyit.federatedsearchjava.federation.SourceDispatcher;
import com.github.guoyyit.federatedsearchjava.federation.SourceWeightRegistry;
import com.github.guoyyit.federatedsearchjava.source.SearchIndexSource;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TestContext {

    final FederatedSearchProperties properties;
    final SourceWeightRegistry weightRegistry;
    final SourceDispatcher dispatcher;
    final FederatedSearchService service;

    TestContext(List<SearchIndexSource> sources) {
        this.properties = new FederatedSearchProperties();
        properties.setGlobalConcurrency(4);
        properties.setSnippetLength(80);
        configureTimeout("a", Duration.ofMillis(200));
        configureTimeout("b", Duration.ofMillis(200));
        configureTimeout("c", Duration.ofMillis(200));
        this.weightRegistry = new SourceWeightRegistry(properties);
        this.dispatcher = new SourceDispatcher(properties);
        ScoreNormalizer normalizer = new ScoreNormalizer();
        Highlighter highlighter = new Highlighter(properties);
        DedupKeyResolver dedupKeyResolver = new DedupKeyResolver(properties);
        ResultMerger merger = new ResultMerger(dedupKeyResolver, normalizer, highlighter, weightRegistry);
        SnapshotStore snapshotStore = new SnapshotStore(properties);
        this.service = new FederatedSearchService(dispatcher, merger, snapshotStore, sources);
    }

    final void configureTimeout(String sourceId, Duration timeout) {
        FederatedSearchProperties.SourceConfig config = new FederatedSearchProperties.SourceConfig();
        config.setTimeout(timeout);
        properties.getSources().put(sourceId, config);
    }

    final void setDedup(DedupStrategy strategy) {
        properties.setDedupStrategy(strategy);
    }

    final void shutdown() {
        dispatcher.shutdown();
    }

    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
