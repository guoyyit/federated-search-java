package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.model.SearchResponse;
import com.github.guoyyit.federatedsearchjava.model.SearchRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightIT {

    private TestContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.shutdown();
        }
    }

    @Test
    void runtimeWeightChangeReordersResultsWithoutRebuild() throws Exception {
        ControllableSource a = ControllableSource.of("a",
                ControllableSource.hit("a", "a-1", "keyboard alpha", 10.0));
        ControllableSource b = ControllableSource.of("b",
                ControllableSource.hit("b", "b-1", "keyboard beta", 10.0));
        context = new TestContext(List.of(a, b));

        context.weightRegistry.setWeight("a", 1.0);
        context.weightRegistry.setWeight("b", 5.0);
        SearchResponse boosted = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));
        assertEquals("b-1", boosted.hits().get(0).docId());
        assertEquals(5.0, boosted.hits().get(0).score(), 1e-9);

        context.weightRegistry.setWeight("b", 0.2);
        SearchResponse lowered = context.service
                .search(new SearchRequest("keyboard", 0, 10, null));
        assertEquals("a-1", lowered.hits().get(0).docId());
    }

    @Test
    void invalidWeightsAreRejected() {
        ControllableSource a = ControllableSource.of("a");
        context = new TestContext(List.of(a));

        assertThrows(IllegalArgumentException.class,
                () -> context.weightRegistry.setWeight("a", -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> context.weightRegistry.setWeight("a", Double.NaN));
        assertEquals(1.0, context.weightRegistry.getWeight("a"), 1e-9);
    }

    @Test
    void unknownSourceDefaultsToWeightOne() {
        context = new TestContext(List.of());
        assertEquals(1.0, context.weightRegistry.getWeight("ghost"), 1e-9);
    }
}
