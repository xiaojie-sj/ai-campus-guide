package com.hbeu.campusguide.algorithm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEmbeddingTest {
    private final LocalEmbedding embedding = new LocalEmbedding();

    @Test
    void producesNormalizedAndStableVectors() {
        double[] first = embedding.embed("学校地址和招生电话");
        double[] second = embedding.embed("学校地址和招生电话");

        assertThat(first).hasSize(LocalEmbedding.DIMENSIONS);
        assertThat(embedding.cosine(first, second)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void relatedTextScoresHigherThanUnrelatedText() {
        double[] query = embedding.embed("学校招生咨询电话");
        double related = embedding.cosine(query, embedding.embed("招生热线电话是多少"));
        double unrelated = embedding.cosine(query, embedding.embed("运动场开放时间"));

        assertThat(related).isGreaterThan(unrelated);
    }
}
