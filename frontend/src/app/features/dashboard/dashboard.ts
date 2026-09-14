import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { Dashboard as DashboardData, SOURCE_LABELS, SourcePortal } from '../../core/models/tender.models';
import { GradeBadge } from '../../shared/grade-badge';
import { ScoreBar } from '../../shared/score-bar';
import { Deadline } from '../../shared/deadline';
import { apiError, timeAgo } from '../../shared/format';

interface HeadlineCard {
  label: string;
  value: number;
  foot: string;
  /** Opens the tender list with this card's filter already applied. */
  params: Record<string, string>;
  tone: 'accent' | 'ok' | 'warn';
  valueClass: string;
  icon: string[];
}

/**
 * A company's landing page: what to look at first today.
 *
 * Every number is the same count the tender list shows under the matching filter, and
 * every card opens the list with that filter applied, so nothing here can disagree
 * with the list it summarises.
 */
@Component({
  selector: 'ts-dashboard',
  standalone: true,
  imports: [RouterLink, DecimalPipe, GradeBadge, ScoreBar, Deadline],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly api = inject(ApiService);
  readonly org = inject(AuthService).company;

  readonly data = signal<DashboardData | null>(null);
  readonly error = signal<string | null>(null);

  readonly timeAgo = timeAgo;

  readonly cards = computed<HeadlineCard[]>(() => {
    const n = this.data()?.numbers;
    if (!n) return [];
    const list: HeadlineCard[] = [
      { label: 'Open tenders', value: n.open, foot: 'ranked for you', params: {}, tone: 'accent', valueClass: '',
        icon: ['M3 6h18', 'M3 12h18', 'M3 18h12'] },
      { label: 'S-grade', value: n.sGrade, foot: 'strongest matches', params: { grade: 'S' }, tone: 'ok', valueClass: 'ok',
        icon: ['m12 3 2.7 5.6 6.3.9-4.5 4.3 1.1 6.2L12 17.1 6.4 20l1.1-6.2L3 9.5l6.3-.9z'] },
      { label: 'A-grade', value: n.aGrade, foot: 'strong matches', params: { grade: 'A' }, tone: 'accent', valueClass: 'accent',
        icon: ['M12 3v18', 'M5 10l7-7 7 7'] },
      { label: 'Closing within 7 days', value: n.closingSoon, foot: 'amber on the list', params: { closingSoon: '1' },
        tone: 'warn', valueClass: 'warn', icon: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M12 7v5.3l3.2 2'] },
      { label: 'Saved', value: n.saved, foot: 'for later', params: { tracked: 'SAVED' }, tone: 'accent', valueClass: '',
        icon: ['M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z'] },
      { label: 'Submitted', value: n.submitted, foot: 'bids you marked', params: { tracked: 'SUBMITTED' }, tone: 'ok',
        valueClass: 'ok', icon: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'm8 12.5 2.8 2.8L16.5 9.5'] },
    ];
    return list;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.error.set(null);
    this.api.getDashboard().subscribe({
      next: (d) => this.data.set(d),
      error: (err) => this.error.set(apiError(err, 'Could not load your dashboard.')),
    });
  }

  sourceLabel(source: SourcePortal): string {
    return SOURCE_LABELS[source];
  }
}
