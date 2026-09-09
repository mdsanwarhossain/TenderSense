import { Component } from '@angular/core';

/** The wordmark above the sign-in and sign-up cards. */
@Component({
  selector: 'ts-auth-brand',
  standalone: true,
  template: `
    <span class="brand-mark" aria-hidden="true">
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 3l7.5 4.3v9.4L12 21l-7.5-4.3V7.3z" /><circle cx="12" cy="12" r="2.6" />
      </svg>
    </span>
    <span>
      <span class="brand-name">TenderSense</span>
      <span class="brand-sub">Bid intelligence</span>
    </span>
  `,
  styles: `
    :host { display: flex; align-items: center; justify-content: center; gap: 11px; }
    .brand-mark {
      display: grid; place-items: center;
      width: 38px; height: 38px; border-radius: 11px;
      background: linear-gradient(150deg, var(--accent) 0%, #8a6bff 100%);
      color: #fff; box-shadow: var(--shadow-pill); flex: none;
    }
    .brand-name { display: block; font-size: 17px; font-weight: 800; letter-spacing: -0.4px; }
    .brand-sub {
      display: block;
      font-size: 10px; font-weight: 700; letter-spacing: 0.5px;
      text-transform: uppercase; color: var(--ink-4);
    }
  `,
})
export class AuthBrand {}
