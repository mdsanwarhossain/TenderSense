import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { GradeBadge } from '../../shared/grade-badge';
import { ScoreBar } from '../../shared/score-bar';
import { EligibilityChip } from '../../shared/eligibility-chip';
import { Deadline } from '../../shared/deadline';
import { MatchGrade, SourcePortal, TenderSummary } from '../../core/models/tender.models';

/** The morning shortlist: the screen the BD team opens first. */
@Component({
  selector: 'ts-shortlist',
  standalone: true,
  imports: [RouterLink, DatePipe, GradeBadge, ScoreBar, EligibilityChip, Deadline],
  templateUrl: './shortlist.html',
  styleUrl: './shortlist.css',
})
export class Shortlist {
  private readonly api = inject(ApiService);

  readonly rows = signal<TenderSummary[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly running = signal(false);

  readonly grades: MatchGrade[] = ['S', 'A', 'B', 'C'];
  readonly activeGrade = signal<MatchGrade | null>(null);
  readonly activeSource = signal<SourcePortal | null>(null);
  readonly includeClosed = signal(false);

  readonly size = 25;
  readonly today = new Date();

  /** The API already ranks and filters; the page renders what it returns. */
  readonly visible = computed(() => this.rows());

  readonly counts = computed(() => {
    const rows = this.rows();
    return {
      s: rows.filter((r) => r.grade === 'S').length,
      a: rows.filter((r) => r.grade === 'A').length,
      urgent: rows.filter((r) => r.urgent).length,
      verify: rows.filter((r) => r.eligibility === 'NEEDS_VERIFICATION').length,
    };
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listTenders({
        page: this.page(),
        size: this.size,
        grade: this.activeGrade() ?? undefined,
        source: this.activeSource() ?? undefined,
        includeClosed: this.includeClosed(),
      })
      .subscribe({
      next: (res) => {
        this.rows.set(res.content);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.loading.set(false);
      },
    });
  }

  toggleGrade(grade: MatchGrade): void {
    this.activeGrade.set(this.activeGrade() === grade ? null : grade);
    this.page.set(0);
    this.load();
  }

  toggleClosed(): void {
    this.includeClosed.update((v) => !v);
    this.page.set(0);
    this.load();
  }

  toggleSource(source: SourcePortal): void {
    this.activeSource.set(this.activeSource() === source ? null : source);
    this.page.set(0);
    this.load();
  }

  nextPage(): void {
    this.page.update((p) => p + 1);
    this.load();
  }

  prevPage(): void {
    if (this.page() === 0) return;
    this.page.update((p) => p - 1);
    this.load();
  }

  runCollection(): void {
    this.running.set(true);
    this.api.runPipeline().subscribe({
      next: () => {
        this.running.set(false);
        this.load();
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.running.set(false);
      },
    });
  }

  /** Surfaces the backend's ProblemDetail message rather than a bare status code. */
  private describe(err: unknown): string {
    const e = err as { error?: { detail?: string }; status?: number; message?: string };
    if (e?.error?.detail) return e.error.detail;
    if (e?.status === 0) return 'Cannot reach the API. Is the backend running on :8080?';
    return e?.message ?? 'Something went wrong loading the shortlist.';
  }
}
