import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AdminDashboard, MatchGrade, SOURCE_LABELS, SourcePortal } from '../../core/models/tender.models';
import { AiQueueCard } from '../../shared/ai-queue-card';
import { GRADE_TINT } from '../../shared/grade-badge';
import { apiError, duration, timeAgo } from '../../shared/format';

/**
 * The admin landing page: the whole platform at a glance. Companies and their accounts,
 * the tender corpus they share, the collection jobs that keep it fresh, and the local
 * model's queue.
 */
@Component({
  selector: 'ts-admin-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, TitleCasePipe, AiQueueCard],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin.css',
})
export class AdminDashboardPage {
  private readonly api = inject(ApiService);

  readonly data = signal<AdminDashboard | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  readonly tint = GRADE_TINT;
  readonly grades: MatchGrade[] = ['S', 'A', 'B', 'C'];
  readonly timeAgo = timeAgo;
  readonly duration = duration;

  /** Corpus totals across every portal, for the footer row and the headline card. */
  readonly corpusTotal = computed(() => {
    const rows = this.data()?.corpus ?? [];
    const sum = (f: (r: (typeof rows)[number]) => number) => rows.reduce((n, r) => n + f(r), 0);
    return {
      total: sum((r) => r.total), open: sum((r) => r.open), closed: sum((r) => r.closed),
      newToday: sum((r) => r.newToday), newThisWeek: sum((r) => r.newThisWeek),
      aiRead: sum((r) => r.aiRead), aiSkipped: sum((r) => r.aiSkipped), aiNone: sum((r) => r.aiNone),
    };
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getAdminDashboard().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiError(err, 'Could not load the admin dashboard.'));
        this.loading.set(false);
      },
    });
  }

  sourceLabel(source: SourcePortal): string {
    return SOURCE_LABELS[source];
  }

  count(g: { s: number; a: number; b: number; c: number }, grade: MatchGrade): number {
    return grade === 'S' ? g.s : grade === 'A' ? g.a : grade === 'B' ? g.b : g.c;
  }
}
