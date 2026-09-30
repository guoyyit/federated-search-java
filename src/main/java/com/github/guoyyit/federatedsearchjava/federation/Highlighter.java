package com.github.guoyyit.federatedsearchjava.federation;

import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
public class Highlighter {

    private static final String FALLBACK_TITLE = "Untitled";
    private static final String ELLIPSIS = "...";

    private final FederatedSearchProperties properties;

    public Highlighter(FederatedSearchProperties properties) {
        this.properties = properties;
    }

    public List<String> terms(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return List.of();
        }
        for (String token : query.trim().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Digit}]+")) {
            if (!token.isEmpty()) {
                terms.add(token);
            }
        }
        return List.copyOf(terms);
    }

    public Result highlight(String title, String content, String query) {
        List<String> terms = terms(query);
        String safeTitle = sanitizeTitle(title);
        String snippet = buildSnippet(content, terms);
        String highlightedTitle = wrapMatches(safeTitle, terms);
        return new Result(highlightedTitle, snippet);
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return FALLBACK_TITLE;
        }
        String trimmed = title.trim();
        if (trimmed.length() <= Math.max(20, properties.getSnippetLength())) {
            return trimmed;
        }
        int limit = Math.max(20, properties.getSnippetLength());
        int cut = trimmed.lastIndexOf(' ', limit);
        if (cut < limit / 2) {
            cut = limit;
        }
        return trimmed.substring(0, cut) + ELLIPSIS;
    }

    private String buildSnippet(String content, List<String> terms) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        int target = Math.max(40, properties.getSnippetLength());
        String window = window(trimmed, terms, target);
        return wrapMatches(window, terms);
    }

    private String window(String text, List<String> terms, int target) {
        int matchIndex = firstMatchIndex(text, terms);
        if (matchIndex < 0 || text.length() <= target) {
            if (text.length() <= target) {
                return text;
            }
            int cut = text.lastIndexOf(' ', target);
            if (cut < target / 2) {
                cut = target;
            }
            return text.substring(0, cut) + ELLIPSIS;
        }
        int start = Math.max(0, matchIndex - target / 3);
        int end = Math.min(text.length(), start + target);
        if (end - start < target) {
            start = Math.max(0, end - target);
        }
        while (start > 0 && !Character.isWhitespace(text.charAt(start))) {
            start--;
        }
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        String snippet = text.substring(start, end).trim();
        if (start > 0) {
            snippet = ELLIPSIS + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + ELLIPSIS;
        }
        return snippet;
    }

    private int firstMatchIndex(String text, List<String> terms) {
        String lower = text.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String term : terms) {
            int index = lower.indexOf(term);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private String wrapMatches(String text, List<String> terms) {
        if (terms.isEmpty()) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(text.length() + 32);
        int cursor = 0;
        while (cursor < text.length()) {
            int bestStart = -1;
            String bestTerm = null;
            for (String term : terms) {
                int index = lower.indexOf(term, cursor);
                if (index >= 0 && (bestStart < 0 || index < bestStart)) {
                    bestStart = index;
                    bestTerm = term;
                }
            }
            if (bestStart < 0) {
                builder.append(text, cursor, text.length());
                break;
            }
            int bestEnd = bestStart + bestTerm.length();
            builder.append(text, cursor, bestStart);
            builder.append(safePrefix());
            builder.append(text, bestStart, bestEnd);
            builder.append(safeSuffix());
            cursor = bestEnd;
        }
        return builder.toString();
    }

    private String safePrefix() {
        String prefix = properties.getHighlightPrefix();
        return prefix == null ? "[" : prefix;
    }

    private String safeSuffix() {
        String suffix = properties.getHighlightSuffix();
        return suffix == null ? "]" : suffix;
    }

    public record Result(String highlightedTitle, String snippet) {
    }
}
