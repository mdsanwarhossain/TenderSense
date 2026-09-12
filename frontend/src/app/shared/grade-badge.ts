import { Component, computed, input } from '@angular/core';
import { MatchGrade } from '../core/models/tender.models';

/**
 * Grade colours, on the same red-to-green scale as the match ring: a light tint with a
 * darker letter of the same hue. Exported so the grade filter shows the same colours.
 */
export const GRADE_TINT: Record<MatchGrade, { bg: string; fg: string }> = {
  S: { bg: 'hsl(142 65% 90%)', fg: 'hsl(142 72% 26%)' },
  A: { bg: 'hsl(100 55% 91%)', fg: 'hsl(100 55% 28%)' },
  B: { bg: 'hsl(48 95% 88%)', fg: 'hsl(38 85% 30%)' },
  C: { bg: 'hsl(0 85% 94%)', fg: 'hsl(0 62% 42%)' },
};

/** S/A/B/C as a tinted tile, so the grade and the score read as one signal. */
@Component({
  selector: 'ts-grade-badge',
  standalone: true,
  template: `<span class="badge" [class.none]="!tint()" [style.background]="tint()?.bg"
                  [style.color]="tint()?.fg">{{ grade() ?? '–' }}</span>`,
  styles: [`
    :host { display: inline-flex; }
    .badge {
      display: inline-flex; align-items: center; justify-content: center;
      width: 34px; height: 30px; border-radius: var(--r-sm);
      font-size: 13px; font-weight: 800;
    }
    .badge.none { color: var(--ink-4); border: 1px dashed var(--line); }

    /* The detail screen leads with the grade, so it gets a larger tile. */
    :host(.big) .badge { width: 52px; height: 48px; font-size: 21px; border-radius: var(--r); }
  `],
})
export class GradeBadge {
  readonly grade = input<MatchGrade | null>(null);
  readonly tint = computed(() => {
    const g = this.grade();
    return g ? GRADE_TINT[g] : null;
  });
}
