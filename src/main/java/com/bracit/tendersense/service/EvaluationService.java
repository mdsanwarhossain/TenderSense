package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.BenchmarkResponse;
import com.bracit.tendersense.entity.Organisation;

public interface EvaluationService {

    /** Runs the held-out comparison: semantic vs keyword, precision@k. */
    BenchmarkResponse benchmark(Organisation organisation);
}
