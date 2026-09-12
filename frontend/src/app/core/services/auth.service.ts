import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SessionUser } from '../models/tender.models';

export interface SignupPayload {
  companyName: string;
  email: string;
  password: string;
  sectors: string[];
}

/**
 * Who is signed in.
 *
 * The session is held server-side (Spring Security) against a same-origin cookie, so
 * there is nothing to attach to a request and nothing to keep in local storage — the
 * browser sends the cookie on its own, and Angular echoes the XSRF-TOKEN cookie on
 * writes. This service only mirrors the answer so the UI can render it.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly user = signal<SessionUser | null>(null);
  /** The signed-in company; null for a platform admin, or when signed out. */
  readonly company = computed(() => this.user()?.organisation ?? null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');

  /**
   * Memoised so the guard, the shell and any screen can all await the same in-flight
   * request rather than each firing their own `/me` on a cold load.
   */
  private pending: Promise<SessionUser | null> | null = null;

  ensureLoaded(): Promise<SessionUser | null> {
    if (this.user()) {
      return Promise.resolve(this.user());
    }
    this.pending ??= firstValueFrom(this.http.get<SessionUser>('/api/auth/me'))
      .then((user) => {
        this.user.set(user);
        return user;
      })
      .catch(() => {
        this.user.set(null);
        return null;
      })
      .finally(() => {
        this.pending = null;
      });
    return this.pending;
  }

  /** Where an account lands: a company on its dashboard, a platform admin on the admin panel. */
  home(user: SessionUser | null = this.user()): string {
    if (!user) return '/login';
    return user.organisation ? '/dashboard' : '/admin';
  }

  async login(email: string, password: string): Promise<SessionUser> {
    const user = await firstValueFrom(
      this.http.post<SessionUser>('/api/auth/login', { email, password }),
    );
    this.user.set(user);
    return user;
  }

  async signup(payload: SignupPayload): Promise<SessionUser> {
    const user = await firstValueFrom(
      this.http.post<SessionUser>('/api/auth/signup', payload),
    );
    this.user.set(user);
    return user;
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
    this.user.set(null);
    this.pending = null;
  }
}
