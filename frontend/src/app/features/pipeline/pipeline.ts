import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { PipelineRun, ProcessingStatus, RunSummary, Schedule } from '../../core/models/tender.models';
import { AiQueueCard } from '../../shared/ai-queue-card';
import { Pager } from '../../shared/pager';
import { apiError, duration } from '../../shared/format';

/**
 * Collection triggers, schedule and run telemetry. Admin only.
 *
 * The run table is read a page at a time, so the cards above it come from the server's
 * totals rather than from the rows on screen.
 */
@Component({
  selector: 'ts-pipeline',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TitleCasePipe, RouterLink, AiQueueCard, Pager],
  templateUrl: './pipeline.html',
  styleUrl: './pipeline.css',
})
export class Pipeline {
  private readonly api = inject(ApiService);

  readonly runs = signal<PipelineRun[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  /** One of the pager's sizes (10 / 25 / 50 / 100), or its dropdown would show a different one. */
  readonly size = signal(25);
  readonly summary = signal<RunSummary | null>(null);
  readonly schedule = signal<Schedule | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly running = signal<'discovery' | 'reconcile' | null>(null);
  /** The staging queue; refreshed every 20 s while the page is open. */
  readonly queue = signal<ProcessingStatus | null>(null);

  readonly duration = duration;

  constructor() {
    this.load();
    this.loadOverview();
    this.loadQueue();
    interval(20_000).pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.loadQueue());
  }

  load(): void {
    this.loading.set(true);
    this.api.getRuns(this.page(), this.size()).subscribe({
      next: (res) => {
        this.runs.set(res.content);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiError(err, 'Could not load pipeline runs.'));
        this.loading.set(false);
      },
    });
  }

  /** The totals and the schedule: both change only when a run finishes. */
  loadOverview(): void {
    this.api.getRunSummary().subscribe({ next: (s) => this.summary.set(s), error: () => this.summary.set(null) });
    this.api.getSchedule().subscribe({ next: (s) => this.schedule.set(s), error: () => this.schedule.set(null) });
  }

  loadQueue(): void {
    this.api.getProcessingStatus().subscribe({
      next: (q) => this.queue.set(q),
      error: () => this.queue.set(null),
    });
  }

  goTo(p: number): void {
    this.page.set(p);
    this.load();
  }

  setSize(size: number): void {
    this.size.set(size);
    this.page.set(0);
    this.load();
  }

  run(mode: 'discovery' | 'reconcile'): void {
    this.running.set(mode);
    this.api.runPipeline(mode === 'reconcile').subscribe({
      next: () => {
        this.running.set(null);
        this.page.set(0);
        this.load();
        this.loadOverview();
      },
      error: (err) => {
        this.error.set(apiError(err, 'The run could not be started.'));
        this.running.set(null);
      },
    });
  }
}
