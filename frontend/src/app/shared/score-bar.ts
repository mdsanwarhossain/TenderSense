import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';

/**
 * The percentage at which the ring is fully green; 0% is red, amber in between. Real
 * scores top out around 70%, so full green comes well before 100% -- otherwise even the
 * best match would never look green.
 */
const GREEN_AT = 60;

/** Hue of the green end: a true green rather than the lime at 120. */
const GREEN_HUE = 142;

const RADIUS = 17;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const COUNT_UP_MS = 1200;

/**
 * Match score as a whole-number percentage inside a ring that fills to it. The score is
 * stored 0..1; people read "53%" faster than "0.527".
 *
 * On first appearance the number counts up from 0 and the ring fills with it, its colour
 * moving from red towards green as it climbs. Anyone whose system asks for reduced
 * motion gets the final state straight away.
 */
@Component({
  selector: 'ts-score-bar',
  standalone: true,
  template: `
    @if (score() !== null) {
      <span class="ring" role="img" [attr.aria-label]="'Match score ' + percent() + '%'"
            [title]="'Match score ' + percent() + '%'">
        <svg viewBox="0 0 44 44" width="44" height="44" aria-hidden="true">
          <circle class="track" cx="22" cy="22" [attr.r]="radius" />
          <circle class="arc" cx="22" cy="22" [attr.r]="radius"
                  [attr.stroke]="arcColor()"
                  [attr.stroke-dasharray]="circumference"
                  [attr.stroke-dashoffset]="offset()" />
        </svg>
        <span class="value" [style.color]="textColor()">{{ shown() }}%</span>
      </span>
    } @else {
      <span class="value none">—</span>
    }`,
  styles: [`
    :host { display: inline-flex; }
    .ring { position: relative; display: inline-grid; place-items: center; width: 44px; height: 44px; }
    /* Rotated so the arc starts at twelve o'clock and fills clockwise. */
    svg { position: absolute; inset: 0; transform: rotate(-90deg); }
    .track { fill: none; stroke: var(--line-soft); stroke-width: 4; }
    .arc { fill: none; stroke-width: 4; stroke-linecap: round; }
    .value {
      position: relative;
      font-size: 11.5px;
      font-weight: 800;
      letter-spacing: -0.3px;
      font-variant-numeric: tabular-nums;
    }
    .value.none { color: var(--ink-4); }
  `],
})
export class ScoreBar {
  readonly score = input<number | null>(null);

  /** Rounded, and clamped so a score above 1 can never overfill the ring. */
  readonly percent = computed(() =>
    Math.max(0, Math.min(100, Math.round((this.score() ?? 0) * 100))));

  /** The number on screen: counts up to percent() rather than jumping to it. */
  readonly shown = signal(0);

  readonly radius = RADIUS;
  readonly circumference = CIRCUMFERENCE;
  readonly offset = computed(() => CIRCUMFERENCE * (1 - this.shown() / 100));

  /** How far along red -> green the ring is, 0..1; it follows the count as it climbs. */
  private readonly t = computed(() => Math.min(1, this.shown() / GREEN_AT));
  private readonly hue = computed(() => Math.round(this.t() * GREEN_HUE));
  /** Gets deeper towards green, so the top of the scale reads as a strong green, not a pale lime. */
  readonly arcColor = computed(() => `hsl(${this.hue()} 80% ${Math.round(48 - this.t() * 12)}%)`);
  /** Same hue, darker, so the number stays readable on white even in the amber band. */
  readonly textColor = computed(() => `hsl(${this.hue()} 78% ${Math.round(32 - this.t() * 6)}%)`);

  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
    // Runs on first render and again if the score ever changes, counting from wherever
    // the number currently stands.
    effect(() => {
      const target = this.percent();
      untracked(() => this.countTo(target));
    });
  }

  private countTo(target: number): void {
    cancelAnimationFrame(this.frame);
    const from = this.shown();
    if (from === target) {
      return;
    }
    if (typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches) {
      this.shown.set(target);
      return;
    }
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / COUNT_UP_MS);
      // Ease in and out: a slow start keeps the red end of the sweep on screen long
      // enough to see, then it speeds up and settles on the score.
      const eased = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
      this.shown.set(Math.round(from + (target - from) * eased));
      if (t < 1) {
        this.frame = requestAnimationFrame(step);
      }
    };
    this.frame = requestAnimationFrame(step);
  }
}
