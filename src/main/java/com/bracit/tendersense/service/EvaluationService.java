package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.BenchmarkResponse;

public interface EvaluationService {

    /** Runs the held-out comparison: semantic vs keyword, precision@k. */
    BenchmarkResponse benchmark();
}
