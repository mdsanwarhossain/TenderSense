import { Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { SOURCE_LABELS, TenderSummary, TrackingState } from '../core/models/tender.models';

/**
 * What the tender team does about a tender: save it for later, mark that they bid on
 * the portal, and open it there.
 *
 * Both toggles answer the click immediately and let the server's reply correct them;
 * a failed request puts the icon back and reports it, so the screen never shows a
 * state the database does not hold.
 */
@Component({
  selector: 'ts-tender-actions',
  standalone: true,
  imports: [DatePipe],
  template: `
    <button type="button" class="act save" [class.on]="wishlisted()" [disabled]="busy()"
            [attr.aria-pressed]="wishlisted()"
            [attr.aria-label]="wishlisted() ? 'Saved for later. Remove from saved' : 'Save for later'"
            [title]="wishlisted() ? 'Saved for later -- click to remove' : 'Save for later'"
            (click)="toggle('wishlist')">
      <svg width="16" height="16" viewBox="0 0 24 24" [attr.fill]="wishlisted() ? 'currentColor' : 'none'"
           stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
           aria-hidden="true">
        <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z" />
      </svg>
    </button>

    <button type="button" class="act submitted" [class.on]="submitted()" [disabled]="busy()"
            [attr.aria-pressed]="submitted()"
            [attr.aria-label]="submitted() ? 'Marked as submitted. Undo' : 'Mark as submitted on the portal'"
            [title]="submitted()
              ? 'Submitted ' + (submittedAt() | date: 'd MMM y') + ' -- click to undo'
              : 'Mark as submitted on the portal'"
            (click)="toggle('submission')">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" [attr.fill]="submitted() ? 'currentColor' : 'none'" />
        <path d="m8 12.5 2.8 2.8L16.5 9.5" [attr.stroke]="submitted() ? 'var(--surface)' : 'currentColor'" />
      </svg>
    </button>

    @if (row().sourceUrl; as url) {
      <a class="act link" [href]="url" target="_blank" rel="noopener noreferrer"
         [title]="'Open on ' + portal()" [attr.aria-label]="'Open on ' + portal() + ' in a new tab'">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M14 4h6v6" /><path d="M20 4 11 13" />
          <path d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4" />
        </svg>
      </a>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 4px; }

    .act {
      display: inline-grid; place-items: center;
      width: 32px; height: 32px; padding: 0;
      border: 1px solid transparent; border-radius: var(--r-sm);
      background: transparent; color: var(--ink-4);
      cursor: pointer;
      transition: background 0.12s ease, color 0.12s ease;
    }
    .act:hover { background: var(--surface-3); color: var(--ink-2); text-decoration: none; }
    .act:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
    .act:disabled { cursor: progress; }

    .save.on { color: var(--accent); background: var(--accent-wash); }
    .submitted.on { color: var(--ok); background: var(--ok-wash); }
    .link:hover { color: var(--accent); }
  `,
})
export class TenderActions {
  private readonly api = inject(ApiService);

  readonly row = input.required<TenderSummary>();
  /** The server's answer after a successful change, so the list can react to it. */
  readonly changed = output<TrackingState>();
  /** A message for the page to show when a change could not be saved. */
  readonly failed = output<string>();

  // Writable local copies, re-seeded whenever a new row arrives: the click shows at
  // once, and the server's reply -- or a failure -- corrects it.
  readonly wishlisted = linkedSignal(() => this.row().wishlisted);
  readonly submitted = linkedSignal(() => this.row().submitted);
  readonly submittedAt = linkedSignal(() => this.row().submittedAt);
  /** One request at a time per row, so two quick clicks cannot race each other. */
  readonly busy = signal(false);

  portal(): string {
    return SOURCE_LABELS[this.row().source];
  }

  toggle(kind: 'wishlist' | 'submission'): void {
    if (this.busy()) return;
    const before = { w: this.wishlisted(), s: this.submitted(), at: this.submittedAt() };
    const on = kind === 'wishlist' ? !before.w : !before.s;

    if (kind === 'wishlist') {
      this.wishlisted.set(on);
    } else {
      this.submitted.set(on);
      this.submittedAt.set(on ? new Date().toISOString() : null);
    }
    this.busy.set(true);

    const id = this.row().id;
    const request = kind === 'wishlist' ? this.api.setWishlisted(id, on) : this.api.setSubmitted(id, on);
    request.subscribe({
      next: (state) => {
        this.wishlisted.set(state.wishlisted);
        this.submitted.set(state.submitted);
        this.submittedAt.set(state.submittedAt);
        this.busy.set(false);
        this.changed.emit(state);
      },
      error: () => {
        this.wishlisted.set(before.w);
        this.submitted.set(before.s);
        this.submittedAt.set(before.at);
        this.busy.set(false);
        this.failed.emit(kind === 'wishlist'
          ? 'Could not update your saved tenders. Try again.'
          : 'Could not update the submitted mark. Try again.');
      },
    });
  }
}
