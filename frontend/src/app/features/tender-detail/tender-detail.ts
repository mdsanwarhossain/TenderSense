import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { GradeBadge } from '../../shared/grade-badge';
import { EligibilityChip } from '../../shared/eligibility-chip';
import {
  BidAction,
  EligibilityReport,
  MatchEvidence,
  TenderDetail as TenderDetailModel,
} from '../../core/models/tender.models';

/**
 * One tender in full, with the evidence behind its score.
 *
 * The evidence panel is the point of this screen: a bid manager should be able to
 * see which capability matched which sentence and judge the match wrong.
 */
@Component({
  selector: 'ts-tender-detail',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, GradeBadge, EligibilityChip],
  templateUrl: './tender-detail.html',
  styleUrl: './tender-detail.css',
})
export class TenderDetail {
  private readonly api = inject(ApiService);

  /** Bound from the route via withComponentInputBinding(). */
  readonly id = input.required<string>();

  readonly tender = signal<TenderDetailModel | null>(null);
  readonly evidence = signal<MatchEvidence | null>(null);
  readonly eligibility = signal<EligibilityReport | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly saving = signal(false);
  readonly savedAction = signal<BidAction | null>(null);

  readonly blockingGaps = computed(
    () => this.eligibility()?.gaps.filter((g) => g.blocking) ?? [],
  );
  readonly checkGaps = computed(
    () => this.eligibility()?.gaps.filter((g) => !g.blocking) ?? [],
  );

  /** Evidence below this similarity is too weak to present as a reason. */
  readonly evidenceFloor = 0.2;

  constructor() {
    queueMicrotask(() => this.load());
  }

  private load(): void {
    const id = Number(this.id());
    if (!Number.isFinite(id)) {
      this.error.set('That is not a valid tender id.');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      tender: this.api.getTender(id),
      evidence: this.api.getEvidence(id),
      eligibility: this.api.getEligibility(id),
    }).subscribe({
      next: ({ tender, evidence, eligibility }) => {
        this.tender.set(tender);
        this.evidence.set(evidence);
        this.eligibility.set(eligibility);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.loading.set(false);
      },
    });
  }

  record(action: BidAction): void {
    const id = Number(this.id());
    this.saving.set(true);
    this.api.recordDecision(id, { action, decidedBy: 'BD Team' }).subscribe({
      next: () => {
        this.savedAction.set(action);
        this.saving.set(false);
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.saving.set(false);
      },
    });
  }

  sourceLabel(source: string | undefined): string {
    return source === 'EGP_BANGLADESH' ? 'e-GP Bangladesh' : 'World Bank';
  }

  private describe(err: unknown): string {
    const e = err as { error?: { detail?: string }; status?: number; message?: string };
    if (e?.status === 404) return 'That tender is not in the corpus.';
    if (e?.error?.detail) return e.error.detail;
    if (e?.status === 0) return 'Cannot reach the API. Is the backend running on :8080?';
    return e?.message ?? 'Something went wrong loading this tender.';
  }
}
