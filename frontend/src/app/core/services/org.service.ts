import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Organisation } from '../models/tender.models';

const STORAGE_KEY = 'tendersense.orgId';

/**
 * Which company the browser is acting for.
 *
 * Holds the selection that {@link orgInterceptor} sends as `X-Org-Id` on every
 * request. The backend resolves the header itself and falls back to the first
 * active organisation when it is absent, so the app renders correctly on a first
 * visit, before this service has loaded anything.
 *
 * Deliberately does not fetch in its constructor: the interceptor injects this
 * service, so a request issued mid-construction would be a circular dependency.
 * {@link load} is called once from the app shell instead.
 */
@Injectable({ providedIn: 'root' })
export class OrgService {
  private readonly http = inject(HttpClient);

  readonly organisations = signal<Organisation[]>([]);
  readonly currentId = signal<number | null>(readStoredId());

  /**
   * Typed nullable on purpose: the list is empty until {@link load} returns, and stays
   * empty if the backend is down. Templates must handle that, not assume a company.
   */
  readonly current = computed<Organisation | null>(() => {
    const all = this.organisations();
    const id = this.currentId();
    return all.find((o) => o.id === id) ?? all.at(0) ?? null;
  });

  load(): void {
    this.http.get<Organisation[]>('/api/organisations').subscribe({
      next: (orgs) => {
        this.organisations.set(orgs);
        // A stored id can outlive the organisation it names — a deactivated tenant,
        // or a database rebuilt with different ids. Drop it rather than sending a
        // header the backend will 404.
        if (!orgs.some((o) => o.id === this.currentId())) {
          this.select(orgs[0]?.id ?? null);
        }
      },
      error: () => this.organisations.set([]),
    });
  }

  select(id: number | null): void {
    this.currentId.set(id);
    try {
      if (id === null) localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, String(id));
    } catch {
      // Private browsing or blocked storage: the selection still holds for this
      // session, it just will not survive a reload.
    }
  }
}

function readStoredId(): number | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw === null ? NaN : Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}
