import { Component, computed, input } from '@angular/core';
import { AiReviewStatus } from '../core/models/tender.models';

/**
 * The local LLM's 0-100 verdict, shown beside the embedding score -- never instead of it,
 * and never used to order the list. Absent until the tender has been reviewed.
 */
@Component({
  selector: 'ts-ai-score',
  standalone: true,
  template: `
    @switch (state()) {
      @case ('scored') {
        <span class="badge" [class.hi]="band() === 'hi'" [class.mid]="band() === 'mid'"
              [class.lo]="band() === 'lo'" [attr.aria-label]="'AI score ' + score() + ' of 100'">{{ score() }}</span>
      }
      @case ('pending') {
        <span class="pending" title="Queued for the local LLM review">pending</span>
      }
      @case ('failed') {
        <span class="none" title="The model gave no valid verdict for this tender">—</span>
      }
      @default {
        <span class="none" title="Not reviewed by the LLM">—</span>
      }
    }`,
  styles: [`
    :host { display: flex; align-items: center; }
    .badge {
      min-width: 34px; padding: 3px 8px; border-radius: var(--r-pill);
      font-family: var(--mono); font-size: 12.5px; font-weight: 600; text-align: center;
    }
    .badge.hi  { background: var(--accent); color: #fff; }
    .badge.mid { background: var(--accent-wash); color: var(--accent-ink); }
    .badge.lo  { background: var(--surface-3); color: var(--ink-3); }
    .pending { font-size: 11px; font-weight: 600; color: var(--ink-4); font-style: italic; }
    .none { color: var(--ink-4); font-size: 13px; }
  `],
})
export class AiScore {
  readonly score = input<number | null>(null);
  readonly status = input<AiReviewStatus | null>(null);

  /** STALE and never-reviewed both render as a dash: an outdated verdict is not an answer. */
  readonly state = computed<'scored' | 'pending' | 'failed' | 'none'>(() => {
    const s = this.status();
    if (s === 'SCORED' && this.score() !== null) return 'scored';
    if (s === 'PENDING') return 'pending';
    if (s === 'FAILED') return 'failed';
    return 'none';
  });

  readonly band = computed(() => {
    const v = this.score() ?? 0;
    return v >= 80 ? 'hi' : v >= 50 ? 'mid' : 'lo';
  });
}
