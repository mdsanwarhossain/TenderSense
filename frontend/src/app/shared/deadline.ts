import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';

/** Closing date with an urgency cue once inside a week. */
@Component({
  selector: 'ts-deadline',
  standalone: true,
  imports: [DatePipe],
  template: `
    @if (closingAt(); as closing) {
      <div class="wrap">
        <span class="mono date" [class.urgent]="urgent()">{{ closing | date: 'dd MMM' }}</span>
        @if (days() !== null) {
          <span class="days" [class.urgent]="urgent()">
            @if (urgent()) {
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
            }
            {{ days()! < 0 ? 'closed' : days() + (days() === 1 ? ' day' : ' days') }}
          </span>
        }
      </div>
    } @else {
      <span class="muted">not stated</span>
    }`,
  styles: [`
    .wrap { display: flex; flex-direction: column; gap: 2px; }
    .date { font-size: 12px; }
    .days { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; color: var(--ink-4); }
    .urgent { color: var(--warn); font-weight: 500; }
  `],
})
export class Deadline {
  readonly closingAt = input<string | null>(null);
  readonly days = input<number | null>(null);
  readonly urgent = computed(() => {
    const d = this.days();
    return d !== null && d >= 0 && d <= 7;
  });
}
