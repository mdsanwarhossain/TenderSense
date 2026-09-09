import { Component, input } from '@angular/core';
import { MatchGrade } from '../core/models/tender.models';

/**
 * S/A/B/C as a single-hue intensity ramp rather than four colours, so colour on
 * the shortlist stays reserved for eligibility and urgency.
 */
@Component({
  selector: 'ts-grade-badge',
  standalone: true,
  template: `<span class="badge" [class]="'g-' + (grade() ?? 'none')">{{ grade() ?? '–' }}</span>`,
  styles: [`
    :host { display: inline-flex; }
    .badge {
      display: inline-flex; align-items: center; justify-content: center;
      width: 34px; height: 30px; border-radius: var(--r-sm);
      font-size: 13px; font-weight: 800;
    }
    .g-S { background: var(--accent); color: #fff; }
    .g-A { background: var(--accent-wash); color: var(--accent-ink); }
    .g-B { background: var(--surface-3); color: var(--ink-3); border: 1px solid var(--line); }
    .g-C, .g-none { color: var(--ink-4); border: 1px dashed var(--line); }

    /* The detail screen leads with the grade, so it gets a larger tile. */
    :host(.big) .badge { width: 52px; height: 48px; font-size: 21px; border-radius: var(--r); }
  `],
})
export class GradeBadge {
  readonly grade = input<MatchGrade | null>(null);
}
