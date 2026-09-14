import { Component } from '@angular/core';

/** The wordmark above the sign-in and sign-up cards. */
@Component({
  selector: 'ts-auth-brand',
  standalone: true,
  template: `
    <img class="brand-logo" src="bracit-tendersense-logo.png" alt="BRAC IT TenderSense" />
  `,
  styles: `
    :host { display: flex; align-items: center; justify-content: center; }
    .brand-logo {
      display: block;
      width: 100%;
      max-width: 240px;
      height: auto;
      border-radius: 10px;
      box-shadow: var(--shadow-pill);
    }
  `,
})
export class AuthBrand {}
