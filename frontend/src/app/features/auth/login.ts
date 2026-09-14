import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthBrand } from './brand';

@Component({
  selector: 'ts-login',
  standalone: true,
  imports: [FormsModule, RouterLink, AuthBrand],
  styleUrl: './auth.css',
  template: `
    <div class="col">
      <ts-auth-brand />

      <div class="card">
        <form (ngSubmit)="submit()">
          <div class="title">
            <h1>Sign in</h1>
            <p>One account per company, shared by your tender team.</p>
          </div>

          @if (error(); as message) {
            <p class="error" role="alert">{{ message }}</p>
          }

          <div class="field">
            <label class="label" for="email">Work email</label>
            <input id="email" name="email" type="email" autocomplete="username"
                   placeholder="tenders@yourcompany.com" required
                   [(ngModel)]="email" [disabled]="busy()" />
          </div>

          <div class="field">
            <label class="label" for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password"
                   placeholder="••••••••" required
                   [(ngModel)]="password" [disabled]="busy()" />
          </div>

          <button class="btn btn-primary" type="submit" [disabled]="busy()">
            @if (busy()) { <span class="spinner light"></span> Signing in… } @else { Sign in }
          </button>
        </form>

        <p class="foot">New company? <a routerLink="/signup">Create an account</a></p>
      </div>

      <div class="stats">
        <span><b>2</b> portals</span>
        <span>collected <b>every 30 min</b></span>
        <span>digest at <b>08:00</b></span>
      </div>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  email = '';
  password = '';
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  async submit(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const user = await this.auth.login(this.email, this.password);
      // Resume whatever they were trying to reach before the guard intervened; otherwise
      // the account's own home -- the company dashboard, or the admin panel.
      const next = this.route.snapshot.queryParamMap.get('next');
      await this.router.navigateByUrl(next && next.startsWith('/') ? next : this.auth.home(user));
    } catch (e: unknown) {
      this.error.set(message(e, 'Email or password is incorrect'));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Surfaces the backend's own wording, which is written for the person reading it. */
export function message(e: unknown, fallback: string): string {
  const detail = (e as { error?: { detail?: string } })?.error?.detail;
  return typeof detail === 'string' && detail.length > 0 ? detail : fallback;
}
