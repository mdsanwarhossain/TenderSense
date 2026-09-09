import { Component, computed, input } from '@angular/core';

/** Score plus a proportional bar. The width is a percentage of the score, never px. */
@Component({
  selector: 'ts-score-bar',
  standalone: true,
  template: `
    <div class="wrap">
      <span class="mono value">{{ score() !== null ? score()!.toFixed(3) : '—' }}</span>
      <span class="track"><span class="fill" [style.width.%]="pct()" [class.strong]="strong()"></span></span>
    </div>`,
  styles: [`
    .wrap { display: flex; flex-direction: column; gap: 4px; }
    .value { font-size: 13px; font-weight: 500; }
    .track { display: block; width: 68px; height: 3px; border-radius: 2px; background: var(--accent-wash); }
    .fill { display: block; height: 3px; border-radius: 2px; background: var(--accent-mid); }
    .fill.strong { background: var(--accent); }
  `],
})
export class ScoreBar {
  readonly score = input<number | null>(null);
  readonly strong = input(false);
  /** Clamped so a score above 1 can never overflow the track. */
  readonly pct = computed(() => Math.max(0, Math.min(100, (this.score() ?? 0) * 100)));
}
