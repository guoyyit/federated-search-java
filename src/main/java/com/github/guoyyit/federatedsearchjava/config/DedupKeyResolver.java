package com.github.guoyyit.federatedsearchjava.config;

import com.github.guoyyit.federatedsearchjava.model.SourceHit;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class DedupKeyResolver {

    private final FederatedSearchProperties properties;

    public DedupKeyResolver(FederatedSearchProperties properties) {
        this.properties = properties;
    }

    public String resolve(SourceHit hit) {
        String normalizedTitle = normalizeText(hit.title());
        String normalizedContent = normalizeText(hit.content());
        String docId = hit.docId() == null ? "" : hit.docId().trim();

        return switch (properties.getDedupStrategy()) {
            case DOC_ID -> {
                if (!docId.isEmpty()) {
                    yield "id:" + docId;
                }
                if (!normalizedTitle.isEmpty()) {
                    yield "title:" + normalizedTitle;
                }
                if (!normalizedContent.isEmpty()) {
                    yield "content:" + fingerprint(normalizedContent);
                }
                yield "unknown:" + hit.sourceId() + ":" + hit.rawScore();
            }
            case TITLE_NORMALIZED -> {
                if (!normalizedTitle.isEmpty()) {
                    yield "title:" + normalizedTitle;
                }
                if (!docId.isEmpty()) {
                    yield "id:" + docId;
                }
                if (!normalizedContent.isEmpty()) {
                    yield "content:" + fingerprint(normalizedContent);
                }
                yield "unknown:" + hit.sourceId() + ":" + hit.rawScore();
            }
            case CONTENT_FINGERPRINT -> {
                if (!normalizedContent.isEmpty()) {
                    yield "content:" + fingerprint(normalizedContent);
                }
                if (!normalizedTitle.isEmpty()) {
                    yield "title:" + normalizedTitle;
                }
                if (!docId.isEmpty()) {
                    yield "id:" + docId;
                }
                yield "unknown:" + hit.sourceId() + ":" + hit.rawScore();
            }
        };
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        String lower = text.trim().toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                builder.append(' ');
            }
        }
        return builder.toString().trim();
    }

    private String fingerprint(String normalizedText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(normalizedText.hashCode());
        }
    }
}
