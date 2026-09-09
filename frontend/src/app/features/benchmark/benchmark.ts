import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { BenchmarkResult } from '../../core/models/tender.models';

/**
 * Semantic vs keyword on the held-out set.
 *
 * The caveat is rendered as prominently as the number on purpose: with a set this
 * small a one-hit difference moves precision@5 by 0.200, and presenting the figure
 * without that context would overclaim.
 */
@Component({
  selector: 'ts-benchmark',
  standalone: true,
  imports: [DecimalPipe, PercentPipe, RouterLink],
  templateUrl: './benchmark.html',
  styleUrl: './benchmark.css',
})
export class Benchmark {
  private readonly api = inject(ApiService);

  readonly result = signal<BenchmarkResult | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly relevantCount = computed(
    () => this.result()?.comparisons.filter((c) => c.labelledRelevant).length ?? 0,
  );

  /** Neither matcher clearly ahead is a real outcome, and it is stated as one. */
  readonly verdict = computed(() => {
    const r = this.result();
    if (!r || r.heldOutCount === 0) return null;
    const delta = r.semantic.precisionAtK - r.keyword.precisionAtK;
    if (Math.abs(delta) < 1e-9) return 'tie';
    return delta > 0 ? 'semantic' : 'keyword';
  });

  constructor() {
    this.api.getBenchmark().subscribe({
      next: (r) => {
        this.result.set(r);
        this.loading.set(false);
      },
      error: (err) => {
        const e = err as { error?: { detail?: string }; status?: number; message?: string };
        this.error.set(
          e?.error?.detail ??
            (e?.status === 0 ? 'Cannot reach the API. Is the backend running on :8080?' : null) ??
            e?.message ??
            'Could not run the benchmark.',
        );
        this.loading.set(false);
      },
    });
  }
}
