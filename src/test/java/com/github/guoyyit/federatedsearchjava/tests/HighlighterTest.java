package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;
import com.github.guoyyit.federatedsearchjava.engine.Highlighter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Annotation must never fail and must be deterministic when fields are null,
 * blank or extremely long.
 */
class HighlighterTest {

    private final Highlighter highlighter = new Highlighter(80, "<em>", "</em>");

    @Test
    void missingFieldsProduceEmptyAnnotations() {
        SearchHit hit = CoordinatorTestSupport.hit("a", "x", null, null, null, 1.0d, "h");

        SearchHit annotated = highlighter.annotate(hit, "federated");

        assertEquals("", annotated.highlightedTitle());
        assertEquals("", annotated.snippet());
    }

    @Test
    void matchedTermsAreHighlighted() {
        SearchHit hit = CoordinatorTestSupport.hit(
                "a", "x", "federated search guide",
                "a short body about federated merging", null, 1.0d, "h");

        SearchHit annotated = highlighter.annotate(hit, "federated");

        assertEquals("<em>federated</em> search guide", annotated.highlightedTitle());
        assertTrue(annotated.snippet().contains("<em>federated</em>"));
    }

    @Test
    void veryLongContentIsTruncatedWithoutUnbalancedMarkers() {
        String content = "federated " + "word ".repeat(500);
        SearchHit hit = CoordinatorTestSupport.hit("a", "x", "title", content, null, 1.0d, "h");

        SearchHit annotated = highlighter.annotate(hit, "federated");

        String snippet = annotated.snippet();
        long opens = snippet.chars().filter(ch -> ch == '<').count();
        assertTrue(opens >= 2, "expect at least open and close markers");
        assertTrue(count(snippet, "<em>") == count(snippet, "</em>"),
                "highlight markers must be balanced");
    }

    @Test
    void outputIsDeterministicForTheSameInput() {
        String content = "federated search merging across sources with normalization";
        SearchHit hit = CoordinatorTestSupport.hit("a", "x", "title", content, null, 1.0d, "h");

        String first = highlighter.annotate(hit, "federated search").snippet();
        String second = highlighter.annotate(hit, "federated search").snippet();

        assertEquals(first, second);
        assertFalse(first.isBlank());
    }

    private static int count(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int pos = text.indexOf(token, from);
            if (pos < 0) {
                return count;
            }
            count++;
            from = pos + token.length();
        }
    }
}
