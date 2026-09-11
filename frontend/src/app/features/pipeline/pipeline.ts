import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { PipelineRun, ProcessingStatus } from '../../core/models/tender.models';

/** Collection triggers, schedule and run telemetry. */
@Component({
  selector: 'ts-pipeline',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TitleCasePipe],
  templateUrl: './pipeline.html',
  styleUrl: './pipeline.css',
})
export class Pipeline {
  private readonly api = inject(ApiService);

  readonly runs = signal<PipelineRun[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly running = signal<'discovery' | 'reconcile' | null>(null);
  /** The staging queue; refreshed every 20 s while the page is open. */
  readonly queue = signal<ProcessingStatus | null>(null);

  /** Mirrors tendersense.schedule.* — all Asia/Dhaka. */
  readonly schedule = [
    { job: 'egpDiscovery', cadence: 'Every 30 min, 08:00–20:30', cost: '1–3 pages' },
    { job: 'egpDetailDrain', cadence: 'Continuous, rate limited', cost: '1 req/sec' },
    { job: 'egpReconcile', cadence: 'Nightly 02:00 — catches corrigenda', cost: '~1 hour' },
    { job: 'worldBankSync', cadence: 'Every 6 hours', cost: 'seconds' },
    { job: 'urgencyRefresh', cadence: 'Daily 05:30', cost: 'seconds' },
    { job: 'morningDigest', cadence: 'Daily 08:00 — the shortlist lands', cost: 'seconds' },
  ];

  readonly lastSuccess = computed(() => this.runs().find((r) => r.status === 'SUCCESS') ?? null);

  readonly failures = computed(() => this.runs().filter((r) => r.status === 'FAILED').length);

  readonly totalScored = computed(() =>
    this.runs().filter((r) => r.status === 'SUCCESS').reduce((n, r) => n + (r.tendersScored ?? 0), 0),
  );

  constructor() {
    this.load();
    this.loadQueue();
    interval(20_000).pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.loadQueue());
  }

  loadQueue(): void {
    this.api.getProcessingStatus().subscribe({
      next: (q) => this.queue.set(q),
      error: () => this.queue.set(null),
    });
  }

  eta(minutes: number | null): string {
    if (minutes === null) return '—';
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    return h < 48 ? `${h} h ${minutes % 60} min` : `${Math.round(h / 24 * 10) / 10} days`;
  }

  load(): void {
    this.loading.set(true);
    this.api.getRuns().subscribe({
      next: (r) => {
        this.runs.set(r);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.loading.set(false);
      },
    });
  }

  run(mode: 'discovery' | 'reconcile'): void {
    this.running.set(mode);
    this.api.runPipeline(mode === 'reconcile').subscribe({
      next: () => {
        this.running.set(null);
        this.load();
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.running.set(null);
      },
    });
  }

  duration(ms: number | null): string {
    if (ms === null) return '—';
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    const m = Math.floor(ms / 60000);
    return `${m}m ${Math.round((ms % 60000) / 1000)}s`;
  }

  private describe(err: unknown): string {
    const e = err as { error?: { detail?: string }; status?: number; message?: string };
    if (e?.error?.detail) return e.error.detail;
    if (e?.status === 0) return 'Cannot reach the API. Is the backend running on :8080?';
    return e?.message ?? 'Could not load pipeline runs.';
  }
}
