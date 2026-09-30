package com.github.guoyyit.federatedsearchjava.config;

import com.github.guoyyit.federatedsearchjava.engine.Highlighter;
import com.github.guoyyit.federatedsearchjava.engine.Deduplicator;
import com.github.guoyyit.federatedsearchjava.engine.ScoreNormalizer;
import com.github.guoyyit.federatedsearchjava.engine.SearchCoordinator;
import com.github.guoyyit.federatedsearchjava.engine.SnapshotStore;
import com.github.guoyyit.federatedsearchjava.source.IndexSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires value-constructed engine beans from {@link SearchProperties}.
 */
@Configuration
public class SearchConfiguration {

    @Bean
    public Highlighter highlighter(SearchProperties properties) {
        int snippetLength = properties.snippetLength() == null ? 160 : properties.snippetLength();
        String open = properties.highlightOpen() == null ? "<em>" : properties.highlightOpen();
        String close = properties.highlightClose() == null ? "</em>" : properties.highlightClose();
        return new Highlighter(snippetLength, open, close);
    }

    @Bean
    public SnapshotStore snapshotStore(SearchProperties properties) {
        long ttlSeconds = properties.snapshotTtlSeconds() == null ? 300L : properties.snapshotTtlSeconds();
        return new SnapshotStore(ttlSeconds);
    }

    @Bean
    public SearchCoordinator searchCoordinator(
            List<IndexSource> sources,
            SearchProperties properties,
            WeightRegistry weights,
            ScoreNormalizer normalizer,
            Deduplicator deduplicator,
            Highlighter highlighter,
            SnapshotStore snapshotStore) {
        return new SearchCoordinator(
                sources, properties, weights, normalizer, deduplicator, highlighter, snapshotStore);
    }
}
