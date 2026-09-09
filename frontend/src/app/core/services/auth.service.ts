import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Organisation } from '../models/tender.models';

export interface SignupPayload {
  companyName: string;
  email: string;
  password: string;
  sectors: string[];
}

/**
 * Who is signed in.
 *
 * The company is held server-side against a same-origin session cookie, so there is
 * nothing to attach to a request and nothing to keep in local storage — the browser
 * sends the cookie on its own. This service only mirrors the answer so the UI can
 * render it.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly company = signal<Organisation | null>(null);

  /**
   * Memoised so the guard, the shell and any screen can all await the same in-flight
   * request rather than each firing their own `/me` on a cold load.
   */
  private pending: Promise<Organisation | null> | null = null;

  ensureLoaded(): Promise<Organisation | null> {
    if (this.company()) {
      return Promise.resolve(this.company());
    }
    this.pending ??= firstValueFrom(this.http.get<Organisation>('/api/auth/me'))
      .then((org) => {
        this.company.set(org);
        return org;
      })
      .catch(() => {
        this.company.set(null);
        return null;
      })
      .finally(() => {
        this.pending = null;
      });
    return this.pending;
  }

  async login(email: string, password: string): Promise<Organisation> {
    const org = await firstValueFrom(
      this.http.post<Organisation>('/api/auth/login', { email, password }),
    );
    this.company.set(org);
    return org;
  }

  async signup(payload: SignupPayload): Promise<Organisation> {
    const org = await firstValueFrom(
      this.http.post<Organisation>('/api/auth/signup', payload),
    );
    this.company.set(org);
    return org;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', null));
    } finally {
      // Clear locally even if the call failed: the user asked to be signed out, and
      // leaving the UI showing a company it can no longer read is worse than a
      // stale server session that expires on its own.
      this.clear();
      await this.router.navigate(['/login']);
    }
  }

  /** Called by the 401 interceptor when a session expires under an open page. */
  clear(): void {
    this.company.set(null);
    this.pending = null;
  }
}
