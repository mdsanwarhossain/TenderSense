import { Component, input } from '@angular/core';
import { EligibilityStatus } from '../core/models/tender.models';

@Component({
  selector: 'ts-eligibility-chip',
  standalone: true,
  template: `
    @switch (status()) {
      @case ('ELIGIBLE') {
        <span class="pill ok">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>
          Eligible
        </span>
      }
      @case ('INELIGIBLE') {
        <span class="pill bad">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.2" stroke-linecap="round"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
          Ineligible
        </span>
      }
      @case ('NEEDS_VERIFICATION') {
        <span class="pill warn">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8v5"/><path d="M12 16.5v.01"/></svg>
          Verify
        </span>
      }
      @default { <span class="pill neutral">—</span> }
    }`,
  styles: [`
    :host { display: inline-flex; }
    /* Shape and icon carry the state as well as colour, so the status survives a
       greyscale print and a colour-blind reader. .pill comes from styles.css. */
  `],
})
export class EligibilityChip {
  readonly status = input<EligibilityStatus | null>(null);
}
