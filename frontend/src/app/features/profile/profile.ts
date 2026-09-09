import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { OrgService } from '../../core/services/org.service';
import { CapabilityProfile } from '../../core/models/tender.models';

/**
 * The selected company's capability profile — what every tender is matched against.
 *
 * The service lines are not decoration: their wording is the single biggest lever
 * on which tenders surface, so the screen names the ones currently causing trouble.
 */
@Component({
  selector: 'ts-profile',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile {
  private readonly api = inject(ApiService);

  /** Named on the page so a company switch is visibly reflected here, not just in the shortlist. */
  readonly org = inject(OrgService).current;

  readonly profile = signal<CapabilityProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly rescoring = signal(false);
  readonly rescored = signal<number | null>(null);

  /**
   * Statements known to pull in work the company does not want. Flagged in the UI so the
   * cause of a bad shortlist is visible on the screen that can fix it.
   */
  private readonly noisyTerms = ['outsourcing', 'technical resource', 'capacity building'];

  readonly isPlaceholder = computed(() => {
    const p = this.profile();
    // The seeded stand-in carries a round 1.2bn turnover and no real reference data.
    return p !== null && p.annualTurnoverBdt === 1200000000;
  });

  noisy(service: string): boolean {
    const s = service.toLowerCase();
    return this.noisyTerms.some((t) => s.includes(t));
  }

  constructor() {
    this.api.getProfile().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.loading.set(false);
      },
    });
  }

  rescore(): void {
    this.rescoring.set(true);
    this.rescored.set(null);
    this.api.runPipeline().subscribe({
      next: (runs) => {
        this.rescored.set(runs.reduce((n, r) => n + (r.tendersScored ?? 0), 0));
        this.rescoring.set(false);
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.rescoring.set(false);
      },
    });
  }

  private describe(err: unknown): string {
    const e = err as { error?: { detail?: string }; status?: number; message?: string };
    if (e?.error?.detail) return e.error.detail;
    if (e?.status === 0) return 'Cannot reach the API. Is the backend running on :8080?';
    return e?.message ?? 'Could not load the capability profile.';
  }
}
