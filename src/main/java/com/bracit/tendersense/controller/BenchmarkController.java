package com.bracit.tendersense.controller;

import com.bracit.tendersense.dto.BenchmarkResponse;
import com.bracit.tendersense.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/** Semantic vs keyword on the held-out set. */
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final EvaluationService evaluationService;

    @GetMapping
    public BenchmarkResponse benchmark() {
        return evaluationService.benchmark();
    }
}
