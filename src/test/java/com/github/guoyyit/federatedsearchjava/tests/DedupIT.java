package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.config.DedupStrategy;
import com.github.guoyyit.federatedsearchjava.model.SearchHit;
import com.github.guoyyit.federatedsearchjava.model.SearchRequest;
import com.github.guoyyit.federatedsearchjava.model.SearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedupIT {

    private TestContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.shutdown();
        }
    }

    @Test
    void sameDocIdAcrossSourcesIsMergedOnceWithAllSourcesRecorded() throws Exception {
        ControllableSource a = ControllableSource.of("a",
                ControllableSource.hit("a", "dup-1", "keyboard review", 5.0));
        ControllableSource b = ControllableSource.slow("b", 30,
                ControllableSource.hit("b", "dup-1", "keyboard review", 9.0));
        ControllableSource c = ControllableSource.of("c",
                ControllableSource.hit("c", "unique-9", "other mouse topic", 1.0));
        context = new TestContext(List.of(a, b, c));

        SearchResponse first = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));

        assertEquals(2, first.totalHits());
        SearchHit merged = first.hits().stream()
                .filter(hit -> hit.docId().equals("dup-1"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("a", "b"), merged.matchedSources(),
                "matchedSources must list every source containing the document");

        SearchResponse reverseOrder = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));
        assertEquals(docIds(first), docIds(reverseOrder),
                "ordering must be stable regardless of source arrival timing");
    }

    @Test
    void titleNormalizedDedupIgnoresPunctuationAndCase() throws Exception {
        ControllableSource a = ControllableSource.of("a",
                ControllableSource.hit("a", "x-1", "Wireless Keyboard!", 5.0));
        ControllableSource b = ControllableSource.of("b",
                ControllableSource.hit("b", "x-2", "wireless keyboard", 5.0));
        context = new TestContext(List.of(a, b));
        context.setDedup(DedupStrategy.TITLE_NORMALIZED);

        SearchResponse response = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));

        assertEquals(1, response.totalHits());
        assertTrue(response.hits().get(0).matchedSources().containsAll(List.of("a", "b")));
    }

    @Test
    void missingFieldsFallBackAndDoNotBreakMerge() throws Exception {
        ControllableSource a = ControllableSource.of("a",
                new com.github.guoyyit.federatedsearchjava.model.SourceHit(
                        "a", "", null, null, 3.0, null));
        context = new TestContext(List.of(a));

        SearchResponse response = context.service
                .search(new SearchRequest("anything", 0, 10, null));

        assertEquals(1, response.totalHits());
        assertEquals("Untitled", response.hits().get(0).title());
        assertEquals("", response.hits().get(0).snippet());
    }

    private List<String> docIds(SearchResponse response) {
        return response.hits().stream().map(SearchHit::docId).toList();
    }
}
