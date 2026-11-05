package com.hbeu.campusguide.algorithm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chinese-aware, deterministic chunker. It prefers sentence boundaries and
 * keeps an overlap so table cross-references and paragraph context survive.
 */
@Component
public class TextChunker {

    public List<String> chunk(String text, int maxChars, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int safeSize = Math.max(120, maxChars);
        int safeOverlap = Math.max(0, Math.min(overlap, safeSize / 2));
        String normalized = text.replace("\r\n", "\n")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        List<String> result = new ArrayList<>();
        int cursor = 0;
        while (cursor < normalized.length()) {
            int hardEnd = Math.min(normalized.length(), cursor + safeSize);
            int end = chooseBoundary(normalized, cursor, hardEnd);
            if (end <= cursor) {
                end = hardEnd;
            }
            String part = normalized.substring(cursor, end).trim();
            if (!part.isBlank()) {
                result.add(part);
            }
            if (end >= normalized.length()) {
                break;
            }
            cursor = Math.max(cursor + 1, end - safeOverlap);
            while (cursor < normalized.length() && Character.isWhitespace(normalized.charAt(cursor))) {
                cursor++;
            }
        }
        return result;
    }

    private int chooseBoundary(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) {
            return text.length();
        }
        String boundaries = "。！？；\n";
        for (int index = hardEnd; index > start + hardEnd / 3; index--) {
            if (boundaries.indexOf(text.charAt(index - 1)) >= 0) {
                return index;
            }
        }
        return hardEnd;
    }
}
