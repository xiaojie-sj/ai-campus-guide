package com.hbeu.campusguide.algorithm;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Small local feature-hashing embedding for offline demonstrations.
 * It is deterministic, requires no model download, and is replaceable by a
 * provider adapter without changing the retrieval contract.
 */
@Component
public class LocalEmbedding {
    public static final int DIMENSIONS = 256;
    public static final String MODEL = "local-feature-hash-v1";

    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        String normalized = text == null ? "" : text.toLowerCase().replaceAll("\\s+", "");
        List<String> features = new ArrayList<>();
        for (int i = 0; i < normalized.length(); i++) {
            features.add(String.valueOf(normalized.charAt(i)));
            if (i + 1 < normalized.length()) {
                features.add(normalized.substring(i, i + 2));
            }
        }
        for (String feature : features) {
            int hash = fnv1a(feature);
            int index = Math.floorMod(hash, DIMENSIONS);
            double sign = ((hash >>> 8) & 1) == 1 ? -1.0 : 1.0;
            vector[index] += sign;
        }
        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm > 0) {
            norm = Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    public double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0.0 : dot / Math.sqrt(leftNorm * rightNorm);
    }

    private int fnv1a(String value) {
        int hash = 0x811c9dc5;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= item & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }
}
