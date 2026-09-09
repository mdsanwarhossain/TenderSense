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
    .badge {
      display: inline-flex; align-items: center; justify-content: center;
      width: 30px; height: 24px; border-radius: var(--radius-sm);
      font-size: 12px; font-weight: 700;
    }
    .g-S { background: var(--accent); color: var(--surface); }
    .g-A { background: var(--accent-tint); color: var(--accent); }
    .g-B { background: var(--accent-wash); color: #46605e; }
    .g-C, .g-none { border: 1px solid var(--line); color: #8a9090; }
  `],
})
export class GradeBadge {
  readonly grade = input<MatchGrade | null>(null);
}
