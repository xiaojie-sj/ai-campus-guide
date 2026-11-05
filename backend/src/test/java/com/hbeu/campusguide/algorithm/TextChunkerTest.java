package com.hbeu.campusguide.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {
    private final TextChunker chunker = new TextChunker();

    @Test
    void keepsChunksWithinConfiguredSizeAndAddsOverlap() {
        String text = ("湖北工程学院新技术学院公开资料用于测试。" ).repeat(40);

        List<String> chunks = chunker.chunk(text, 160, 30);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 160);
        String firstTail = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 12));
        assertThat(chunks.get(1)).contains(firstTail);
    }

    @Test
    void ignoresBlankInput() {
        assertThat(chunker.chunk("  \n\t ", 200, 20)).isEmpty();
    }
}
