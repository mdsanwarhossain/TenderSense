package com.bracit.tendersense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirms the local ONNX embedding model works with no API key and no network at
 * inference time, and that it produces the 384 dimensions the pgvector schema
 * declares. A mismatch here would silently corrupt every stored score.
 */
@SpringBootTest
class EmbeddingSmokeTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    @DisplayName("embeds text into 384 dimensions")
    void producesExpectedDimensions() {
        float[] vector = embeddingModel.embed("ICT systems implementation and digital governance");

        assertNotNull(vector);
        assertEquals(384, vector.length,
                "spring.ai.vectorstore.pgvector.dimensions must match the model");

        boolean allZero = true;
        for (float v : vector) {
            if (v != 0f) {
                allZero = false;
                break;
            }
        }
        assertFalse(allZero, "embedding is all zeros - the model did not actually run");
    }

    @Test
    @DisplayName("semantic similarity beats vocabulary overlap")
    void semanticSimilarityWorks() {
        // The BRD's core claim, as an executable assertion: BracIT's profile wording
        // and a tender's wording share almost no keywords but mean the same thing,
        // and that pair must score higher than an unrelated tender.
        float[] profile = embeddingModel.embed("ICT systems implementation");
        float[] related = embeddingModel.embed("capacity building in digital governance");
        float[] unrelated = embeddingModel.embed("supply of frozen fish and cold storage");

        double relatedScore = cosine(profile, related);
        double unrelatedScore = cosine(profile, unrelated);

        System.out.printf("cosine: related=%.4f unrelated=%.4f%n", relatedScore, unrelatedScore);

        assertTrue(relatedScore > unrelatedScore,
                "semantic match (%.4f) did not beat unrelated text (%.4f)"
                        .formatted(relatedScore, unrelatedScore));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
