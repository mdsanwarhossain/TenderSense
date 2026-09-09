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
  /** Clamped so a score above 1 can never overflow the track. */
  readonly pct = computed(() => Math.max(0, Math.min(100, (this.score() ?? 0) * 100)));
}
