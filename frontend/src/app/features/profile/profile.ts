import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import {
  CapabilityProfile, ProfileStaleness, SectorOption,
} from '../../core/models/tender.models';

/** The editable shape. Mirrors CapabilityProfileDto minus the server-assigned ids. */
interface Draft {
  orgName: string;
  summary: string;
  annualTurnoverBdt: number | null;
  services: string[];
  exclusions: string[];
  sectors: string[];
  geographies: string[];
  pastProjects: {
    title: string; client: string; description: string;
    sector: string; valueBdt: number | null; year: number | null;
  }[];
  certifications: { code: string; name: string; validUntil: string }[];
}

/**
 * The screen where the product's accuracy is actually controlled.
 *
 * Service lines and exclusions get one row each rather than a textarea, because each
 * statement is embedded separately — a merged paragraph scores worse than the same
 * words split apart.
 */
@Component({
  selector: 'ts-profile',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class Profile {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  /** Named on the page so a company switch is visibly reflected here. */
  readonly org = inject(AuthService).company;

  readonly draft = signal<Draft | null>(null);
  readonly sectorOptions = signal<SectorOption[]>([]);
  readonly staleness = signal<ProfileStaleness | null>(null);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly rescoring = signal(false);
  readonly error = signal<string | null>(null);
  readonly saved = signal(false);
  readonly rescored = signal<number | null>(null);

  /** True on the first visit after signing up, when the profile is still empty. */
  readonly welcome = signal(false);

  /** Serialised snapshot of the last saved state; cheaper than a deep comparison. */
  private clean = '';

  readonly dirty = computed(() => {
    const d = this.draft();
    return d !== null && JSON.stringify(d) !== this.clean;
  });

  readonly empty = computed(() => (this.draft()?.services.length ?? 0) === 0);

  /**
   * Statements known to pull in work the company does not want. Flagged in the UI so the
   * cause of a bad shortlist is visible on the screen that can fix it.
   */
  private readonly noisyTerms = ['outsourcing', 'technical resource', 'capacity building'];

  noisy(service: string): boolean {
    const s = service.toLowerCase();
    return this.noisyTerms.some((t) => s.includes(t));
  }

  constructor() {
    this.welcome.set(this.route.snapshot.queryParamMap.get('welcome') === '1');
    this.api.listSectors().subscribe({ next: (s) => this.sectorOptions.set(s) });
    this.load();
  }

  private load(): void {
    this.api.getProfile().subscribe({
      next: (p) => {
        this.draft.set(this.toDraft(p));
        this.clean = JSON.stringify(this.draft());
        this.loading.set(false);
        this.refreshStaleness();
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.loading.set(false);
      },
    });
  }

  private refreshStaleness(): void {
    this.api.getStaleness().subscribe({ next: (s) => this.staleness.set(s) });
  }

  private toDraft(p: CapabilityProfile | null): Draft {
    return {
      orgName: p?.orgName ?? this.org()?.name ?? '',
      summary: p?.summary ?? '',
      annualTurnoverBdt: p?.annualTurnoverBdt ?? null,
      services: [...(p?.services ?? [])],
      exclusions: [...(p?.exclusions ?? [])],
      sectors: [...(p?.sectors ?? this.org()?.sectors ?? [])],
      geographies: [...(p?.geographies ?? [])],
      pastProjects: (p?.pastProjects ?? []).map((x) => ({
        title: x.title ?? '', client: x.client ?? '', description: x.description ?? '',
        sector: x.sector ?? '', valueBdt: x.valueBdt, year: x.year,
      })),
      certifications: (p?.certifications ?? []).map((c) => ({
        code: c.code ?? '', name: c.name ?? '', validUntil: c.validUntil ?? '',
      })),
    };
  }

  /** Every mutation goes through here so `dirty` always sees a new object identity. */
  private edit(change: (d: Draft) => void): void {
    const d = this.draft();
    if (!d) return;
    const next: Draft = structuredClone(d);
    change(next);
    this.draft.set(next);
    this.saved.set(false);
  }

  // ---- list editing -------------------------------------------------------

  setLine(key: 'services' | 'exclusions' | 'geographies', i: number, value: string): void {
    this.edit((d) => { d[key][i] = value; });
  }
  addLine(key: 'services' | 'exclusions' | 'geographies'): void {
    this.edit((d) => { d[key].push(''); });
  }
  removeLine(key: 'services' | 'exclusions' | 'geographies', i: number): void {
    this.edit((d) => { d[key].splice(i, 1); });
  }

  toggleSector(value: string): void {
    this.edit((d) => {
      const i = d.sectors.indexOf(value);
      i >= 0 ? d.sectors.splice(i, 1) : d.sectors.push(value);
    });
  }
  hasSector(value: string): boolean {
    return this.draft()?.sectors.includes(value) ?? false;
  }

  addCertification(): void {
    this.edit((d) => { d.certifications.push({ code: '', name: '', validUntil: '' }); });
  }
  removeCertification(i: number): void {
    this.edit((d) => { d.certifications.splice(i, 1); });
  }
  setCertification(i: number, field: 'code' | 'name' | 'validUntil', value: string): void {
    this.edit((d) => { d.certifications[i][field] = value; });
  }

  addProject(): void {
    this.edit((d) => {
      d.pastProjects.push({
        title: '', client: '', description: '', sector: '', valueBdt: null, year: null,
      });
    });
  }
  removeProject(i: number): void {
    this.edit((d) => { d.pastProjects.splice(i, 1); });
  }
  setProject(i: number, field: 'title' | 'client' | 'description' | 'sector', value: string): void {
    this.edit((d) => { d.pastProjects[i][field] = value; });
  }
  setProjectNumber(i: number, field: 'valueBdt' | 'year', value: string): void {
    const n = value.trim() === '' ? null : Number(value);
    this.edit((d) => { d.pastProjects[i][field] = Number.isFinite(n as number) ? n : null; });
  }

  setName(value: string): void { this.edit((d) => { d.orgName = value; }); }
  setSummary(value: string): void { this.edit((d) => { d.summary = value; }); }
  setTurnover(value: string): void {
    const n = value.trim() === '' ? null : Number(value.replace(/[^0-9.]/g, ''));
    this.edit((d) => { d.annualTurnoverBdt = Number.isFinite(n as number) ? n : null; });
  }

  // ---- actions ------------------------------------------------------------

  save(): void {
    const d = this.draft();
    if (!d || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    this.api.updateProfile(d).subscribe({
      next: (p) => {
        this.draft.set(this.toDraft(p));
        this.clean = JSON.stringify(this.draft());
        this.saving.set(false);
        this.saved.set(true);
        this.welcome.set(false);
        this.refreshStaleness();
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.saving.set(false);
      },
    });
  }

  discard(): void {
    this.draft.set(JSON.parse(this.clean) as Draft);
    this.error.set(null);
  }

  /**
   * Re-scores this company's corpus. Separate from saving on purpose: saving is instant,
   * this takes a pipeline lock for the better part of a minute.
   */
  rescore(): void {
    if (this.rescoring()) return;
    this.rescoring.set(true);
    this.rescored.set(null);
    this.error.set(null);
    this.api.rescore().subscribe({
      next: (run) => {
        this.rescored.set(run.tendersScored ?? 0);
        this.rescoring.set(false);
        this.refreshStaleness();
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
