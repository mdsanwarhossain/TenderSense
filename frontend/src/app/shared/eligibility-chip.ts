import { Component, input } from '@angular/core';
import { EligibilityStatus } from '../core/models/tender.models';

@Component({
  selector: 'ts-eligibility-chip',
  standalone: true,
  template: `
    @switch (status()) {
      @case ('ELIGIBLE') {
        <span class="chip ok">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
          Eligible
        </span>
      }
      @case ('INELIGIBLE') {
        <span class="chip bad">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.2" stroke-linecap="round"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
          Ineligible
        </span>
      }
      @case ('NEEDS_VERIFICATION') {
        <span class="chip warn">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8v5"/><path d="M12 16.5v.01"/></svg>
          Verify
        </span>
      }
      @default { <span class="chip none">—</span> }
    }`,
  styles: [`
    .chip { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; }
    .ok { color: var(--ok); }
    .warn { color: var(--warn); }
    .bad { color: #9c2f2f; }
    .none { color: var(--ink-4); }
  `],
})
export class EligibilityChip {
  readonly status = input<EligibilityStatus | null>(null);
}
