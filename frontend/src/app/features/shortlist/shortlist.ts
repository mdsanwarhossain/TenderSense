import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { GradeBadge } from '../../shared/grade-badge';
import { ScoreBar } from '../../shared/score-bar';
import { Deadline } from '../../shared/deadline';
import { TenderActions } from '../../shared/tender-actions';
import {
  MatchGrade, SOURCE_LABELS, SOURCE_OPTIONS, SourcePortal, TenderListSummary, TenderSummary,
  TrackingFilter, TrackingState,
} from '../../core/models/tender.models';

/**
 * The tender list: the screen the BD team opens first.
 *
 * The summary cards double as filters -- S-grade, closing this week, saved, submitted --
 * and combine with each other and with the dropdowns. Their counts come from the
 * database and follow only source and Include closed, so clicking a card changes the
 * table, never the numbers on the other cards.
 */
@Component({
  selector: 'ts-shortlist',
  standalone: true,
  imports: [RouterLink, GradeBadge, ScoreBar, Deadline, TenderActions],
  templateUrl: './shortlist.html',
  styleUrl: './shortlist.css',
})
export class Shortlist {
  private readonly api = inject(ApiService);

  /** Named on the page so a company switch is visible here, not just in the ranking. */
  readonly org = inject(AuthService).company;

  readonly rows = signal<TenderSummary[]>([]);
  readonly total = signal(0);
  /** Corpus-wide counts for the summary cards, as opposed to the page of rows on screen. */
  readonly summary = signal<TenderListSummary | null>(null);
  readonly page = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** A save / submit that could not be recorded. Shown above the list, not instead of it. */
  readonly actionError = signal<string | null>(null);
  readonly running = signal(false);

  readonly grades: MatchGrade[] = ['S', 'A', 'B', 'C'];
  readonly sources = SOURCE_OPTIONS;
  readonly activeGrade = signal<MatchGrade | null>(null);
  readonly activeSource = signal<SourcePortal | null>(null);
  readonly includeClosed = signal(false);
  readonly activeTracked = signal<TrackingFilter | null>(null);
  /** The "Closing within 7 days" card's filter. */
  readonly closingSoon = signal(false);

  sourceLabel(source: SourcePortal): string {
    return SOURCE_LABELS[source];
  }

  readonly size = 25;

  /**
   * Free-text narrowing of the page already fetched. The API ranks and filters
   * server-side; this only hides rows the reader is not looking at right now, so
   * it is labelled as filtering the page rather than searching the corpus.
   */
  readonly query = signal('');

  readonly visible = computed(() => {
    const q = this.query().trim().toLowerCase();
    const rows = this.rows();
    if (!q) return rows;
    return rows.filter((r) =>
      `${r.title ?? ''} ${r.procuringEntity ?? ''}`.toLowerCase().includes(q),
    );
  });

  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.total() / this.size)));

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.loadSummary();
    this.api
      .listTenders({
        page: this.page(),
        size: this.size,
        grade: this.activeGrade() ?? undefined,
        source: this.activeSource() ?? undefined,
        includeClosed: this.includeClosed(),
        tracked: this.activeTracked() ?? undefined,
        closingSoon: this.closingSoon(),
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

  /** Scoped by source and Include closed only -- see the class comment. */
  private loadSummary(): void {
    this.api
      .getListSummary({
        source: this.activeSource() ?? undefined,
        includeClosed: this.includeClosed(),
      })
      .subscribe({
        next: (s) => this.summary.set(s),
        error: () => this.summary.set(null),
      });
  }

  filter(value: string): void {
    this.query.set(value);
  }

  setGrade(value: string): void {
    this.activeGrade.set((value || null) as MatchGrade | null);
    this.page.set(0);
    this.load();
  }

  setSource(value: string): void {
    this.activeSource.set((value || null) as SourcePortal | null);
    this.page.set(0);
    this.load();
  }

  /** The S-grade card is the grade filter set to S; the dropdown shows the same state. */
  toggleSGrade(): void {
    this.setGrade(this.activeGrade() === 'S' ? '' : 'S');
  }

  toggleClosingSoon(): void {
    this.closingSoon.update((v) => !v);
    this.page.set(0);
    this.load();
  }

  toggleClosed(): void {
    this.includeClosed.update((v) => !v);
    this.page.set(0);
    this.load();
  }

  /** Saved and Submitted are alternatives: choosing one clears the other. */
  toggleTracked(filter: TrackingFilter): void {
    this.activeTracked.set(this.activeTracked() === filter ? null : filter);
    this.page.set(0);
    this.load();
  }

  /**
   * Keeps the page honest after a save or submit: the Saved / Submitted card counts move,
   * and a row that no longer belongs under the active filter leaves the list.
   */
  onTracked(state: TrackingState): void {
    this.actionError.set(null);
    this.loadSummary();
    const filter = this.activeTracked();
    const leaves = (filter === 'SAVED' && !state.wishlisted)
      || (filter === 'SUBMITTED' && !state.submitted);
    if (leaves) {
      this.rows.update((rows) => rows.filter((r) => r.id !== state.tenderId));
      this.total.update((t) => Math.max(0, t - 1));
      return;
    }
    this.rows.update((rows) => rows.map((r) => r.id === state.tenderId
      ? { ...r, wishlisted: state.wishlisted, submitted: state.submitted, submittedAt: state.submittedAt }
      : r));
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
    return e?.message ?? 'Something went wrong loading the tender list.';
  }
}
