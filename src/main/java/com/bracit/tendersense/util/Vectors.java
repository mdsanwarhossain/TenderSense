package com.bracit.tendersense.util;

public final class Vectors {

    private Vectors() {
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0d;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0d;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Clamps into 0..1 so downstream grading never sees a negative similarity. */
    public static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, v));
    }
}
