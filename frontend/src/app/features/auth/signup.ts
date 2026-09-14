import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthBrand } from './brand';
import { message } from './login';

interface SectorOption { value: string; label: string; }

@Component({
  selector: 'ts-signup',
  standalone: true,
  imports: [FormsModule, RouterLink, AuthBrand],
  styleUrl: './auth.css',
  template: `
    <div class="col wide">
      <ts-auth-brand />

      <div class="card">
        <form (ngSubmit)="submit()">
          <div class="title">
            <h1>Create a company account</h1>
            <p>Takes about a minute. You can refine the profile afterwards.</p>
          </div>

          @if (error(); as m) {
            <p class="error" role="alert">{{ m }}</p>
          }

          <div class="field">
            <label class="label" for="company">Company name</label>
            <input id="company" name="company" type="text" required
                   placeholder="Meghna Engineering Works Ltd"
                   [(ngModel)]="companyName" [disabled]="busy()" />
          </div>

          <div class="row-2">
            <div class="field">
              <label class="label" for="email">Work email</label>
              <input id="email" name="email" type="email" autocomplete="username" required
                     placeholder="bids@yourcompany.com"
                     [(ngModel)]="email" [disabled]="busy()" />
            </div>
            <div class="field">
              <label class="label" for="password">Password</label>
              <input id="password" name="password" type="password" autocomplete="new-password"
                     required placeholder="At least 8 characters"
                     [(ngModel)]="password" [disabled]="busy()" />
            </div>
          </div>

          <div class="field">
            <span class="label" id="sectors-label">Sectors you bid in</span>
            <div class="sectors" role="group" aria-labelledby="sectors-label">
              @for (s of sectors(); track s.value) {
                <button type="button" [class.on]="chosen().has(s.value)"
                        [attr.aria-pressed]="chosen().has(s.value)"
                        (click)="toggle(s.value)" [disabled]="busy()">
                  {{ s.label }}
                </button>
              }
            </div>
            <span class="help">
              You will only be shown tenders tagged with these. Change them any time in your profile.
            </span>
          </div>

          <button class="btn btn-primary" type="submit" [disabled]="busy()">
            @if (busy()) { <span class="spinner light"></span> Creating… } @else { Create account }
          </button>
        </form>

        <p class="foot">Already registered? <a routerLink="/login">Sign in</a></p>
      </div>
    </div>
  `,
})
export class Signup {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  companyName = '';
  email = '';
  password = '';

  readonly sectors = signal<SectorOption[]>([]);
  readonly chosen = signal<Set<string>>(new Set());
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.http.get<SectorOption[]>('/api/sectors').subscribe({
      next: (list) => this.sectors.set(list),
      error: () => this.error.set('Could not load the sector list. Reload the page.'),
    });
  }

  toggle(value: string): void {
    // A new Set each time: mutating in place would not change the signal's identity
    // and the chips would not repaint.
    const next = new Set(this.chosen());
    next.has(value) ? next.delete(value) : next.add(value);
    this.chosen.set(next);
  }

  async submit(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.signup({
        companyName: this.companyName,
        email: this.email,
        password: this.password,
        sectors: [...this.chosen()],
      });
      // Straight to the profile editor, not the shortlist: a company with sectors but
      // no capability statements can be gated but not scored, so its shortlist would
      // be empty and look broken.
      await this.router.navigate(['/profile'], { queryParams: { welcome: 1 } });
    } catch (e: unknown) {
      this.error.set(message(e, 'Could not create the account'));
    } finally {
      this.busy.set(false);
    }
  }
}
