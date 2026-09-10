import { Component, computed, input } from '@angular/core';

/**
 * Match score as a whole-number percentage, plus a proportional bar. The score is stored
 * 0..1; people read "53%" faster than "0.527", and the extra decimals claimed a precision
 * the ranking does not have.
 */
@Component({
  selector: 'ts-score-bar',
  standalone: true,
  template: `
    <div class="wrap">
      <span class="mono value">{{ score() !== null ? percent() + '%' : '—' }}</span>
      <span class="track"><span class="fill" [style.width.%]="percent()" [class.strong]="strong()"></span></span>
    </div>`,
  styles: [`
    .wrap { display: flex; flex-direction: column; gap: 5px; }
    .value { font-size: 13px; font-weight: 600; }
    .track { display: block; width: 74px; height: 5px; border-radius: 99px; background: var(--accent-wash); overflow: hidden; }
    .fill { display: block; height: 100%; border-radius: 99px; background: var(--accent); opacity: 0.55; }
    .fill.strong { opacity: 1; }
  `],
})
export class ScoreBar {
  readonly score = input<number | null>(null);
  readonly strong = input(false);
  /** Rounded, and clamped so a score above 1 can never overflow the track. */
  readonly percent = computed(() =>
    Math.max(0, Math.min(100, Math.round((this.score() ?? 0) * 100))));
}
