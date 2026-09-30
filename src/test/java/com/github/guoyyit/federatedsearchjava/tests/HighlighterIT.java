package com.github.guoyyit.federatedsearchjava.tests;

import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;
import com.github.guoyyit.federatedsearchjava.federation.Highlighter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighlighterIT {

    private final Highlighter highlighter = new Highlighter(new FederatedSearchProperties());

    @Test
    void wrapsMatchingTermsAndExtractsDeterministicTerms() {
        Highlighter.Result result = highlighter.highlight(
                "Wireless Keyboard", "A wireless keyboard with long battery life", "wireless keyboard");

        assertTrue(result.highlightedTitle().contains("[Keyboard]"));
        assertTrue(result.snippet().contains("[wireless]"));
        assertEquals(java.util.List.of("wireless", "keyboard"),
                highlighter.terms("Wireless,  KEYBOARD!!"));
    }

    @Test
    void missingContentProducesEmptySnippetAndFallbackTitle() {
        Highlighter.Result result = highlighter.highlight(null, null, "keyboard");
        assertEquals("Untitled", result.highlightedTitle());
        assertEquals("", result.snippet());
    }

    @Test
    void longContentIsTruncatedToBoundedSnippet() {
        String content = "prefix ".repeat(50) + " keyboard " + " suffix".repeat(50);
        Highlighter.Result result = highlighter.highlight("Title", content, "keyboard");
        assertTrue(result.snippet().length() <= 240,
                "snippet must be bounded, was " + result.snippet().length());
        assertTrue(result.snippet().contains("[keyboard]"));
        assertFalse(result.snippet().startsWith("prefix prefix"),
                "snippet should be a window around the match");
    }

    @Test
    void queryWithoutMatchesReturnsPlainBoundedPrefix() {
        String content = "a".repeat(500);
        Highlighter.Result result = highlighter.highlight("Title", content, "zzzz");
        assertTrue(result.snippet().endsWith("..."));
        assertTrue(result.snippet().length() <= 200);
    }
}
