package com.github.guoyyit.federatedsearchjava.engine;

import com.github.guoyyit.federatedsearchjava.domain.SearchHit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Produces deterministic highlighted title and content snippet. Missing
 * fields, overlong content or malformed input never fail the merge: they
 * fall back to empty highlights / truncated plain text.
 */
public class Highlighter {

    private static final int MAX_QUERY_TERMS = 16;
    private static final int SNIPPET_PADDING = 40;

    private final int snippetLength;
    private final String open;
    private final String close;

    public Highlighter(int snippetLength, String open, String close) {
        this.snippetLength = Math.max(40, snippetLength);
        this.open = open == null ? "<em>" : open;
        this.close = close == null ? "</em>" : close;
    }

    public SearchHit annotate(SearchHit hit, String query) {
        List<String> terms = tokenize(query);
        String title = hit.title() == null ? "" : hit.title();
        String content = hit.content() == null ? "" : hit.content();

        String snippet = buildSnippet(content, terms);
        String highlightedTitle = highlight(title, terms, Integer.MAX_VALUE);

        return new SearchHit(
                hit.documentId(),
                hit.source(),
                title,
                content,
                hit.url(),
                hit.rawScore(),
                hit.normalizedScore(),
                hit.finalScore(),
                highlightedTitle,
                snippet,
                hit.attributes(),
                hit.indexedAt(),
                hit.contentHash());
    }

    /**
     * Window of at most {@code snippetLength} characters around the first
     * matched term; leading/trailing ellipses mark truncated text. No match
     * or blank content yields a plain truncated prefix.
     */
    private String buildSnippet(String content, List<String> terms) {
        String safe = content.replaceAll("\\s+", " ").trim();
        if (safe.isEmpty()) {
            return "";
        }
        int start = 0;
        if (!terms.isEmpty()) {
            String lower = safe.toLowerCase(Locale.ROOT);
            int first = -1;
            for (String term : terms) {
                int pos = lower.indexOf(term);
                if (pos >= 0 && (first < 0 || pos < first)) {
                    first = pos;
                }
            }
            if (first >= 0) {
                start = Math.max(0, first - SNIPPET_PADDING);
            }
        }
        int end = Math.min(safe.length(), start + snippetLength);
        String window = safe.substring(start, end);
        String body = highlight(window, terms, snippetLength);
        return (start > 0 ? "…" : "") + body + (end < safe.length() ? "…" : "");
    }

    private String highlight(String text, List<String> terms, int maxLength) {
        if (terms.isEmpty() || text.isEmpty()) {
            return truncatePlain(text, maxLength);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean[] mark = new boolean[text.length()];
        for (String term : terms) {
            int from = 0;
            int pos;
            while ((pos = lower.indexOf(term, from)) >= 0) {
                for (int i = pos; i < pos + term.length() && i < mark.length; i++) {
                    mark[i] = true;
                }
                from = pos + term.length();
            }
        }
        StringBuilder result = new StringBuilder();
        boolean inside = false;
        boolean truncated = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (mark[i] && !inside) {
                result.append(open);
                inside = true;
            } else if (!mark[i] && inside) {
                result.append(close);
                inside = false;
            }
            result.append(ch);
            if (maxLength != Integer.MAX_VALUE && visibleLength(result) >= maxLength) {
                if (inside) {
                    result.append(close);
                    inside = false;
                }
                truncated = i + 1 < text.length();
                break;
            }
        }
        if (inside) {
            result.append(close);
        }
        if (truncated) {
            result.append("…");
        }
        return result.toString();
    }

    private int visibleLength(CharSequence marked) {
        return marked.length()
                - countOccurrences(marked, open) * open.length()
                - countOccurrences(marked, close) * close.length();
    }

    private static int countOccurrences(CharSequence text, String token) {
        if (token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        String value = text.toString();
        while (true) {
            int pos = value.indexOf(token, from);
            if (pos < 0) {
                return count;
            }
            count++;
            from = pos + token.length();
        }
    }

    private String truncatePlain(String text, int maxLength) {
        if (maxLength == Integer.MAX_VALUE || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }

    private static List<String> tokenize(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return terms;
        }
        for (String raw : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+")) {
            if (!raw.isEmpty() && !terms.contains(raw)) {
                terms.add(raw);
                if (terms.size() >= MAX_QUERY_TERMS) {
                    break;
                }
            }
        }
        return terms;
    }
}
