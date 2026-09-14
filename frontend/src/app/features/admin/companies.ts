import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { AdminCompany, AdminCompanyDetail } from '../../core/models/tender.models';
import { GRADE_TINT } from '../../shared/grade-badge';
import { apiError, timeAgo } from '../../shared/format';

/**
 * Every company on the platform. Switching one off stops its account signing in (and
 * signs out an open session on its next request); its tenders, scores and profile stay,
 * so switching it back on restores everything.
 */
@Component({
  selector: 'ts-admin-companies',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './companies.html',
  styleUrls: ['./admin.css', './companies.css'],
})
export class AdminCompanies {
  private readonly api = inject(ApiService);

  readonly rows = signal<AdminCompany[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal<number | null>(null);
  readonly query = signal('');
  readonly expanded = signal<number | null>(null);
  readonly detail = signal<AdminCompanyDetail | null>(null);

  readonly tint = GRADE_TINT;
  readonly timeAgo = timeAgo;

  readonly visible = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) return this.rows();
    return this.rows().filter((c) =>
      `${c.name} ${c.slug} ${c.accountEmail ?? ''}`.toLowerCase().includes(q));
  });

  readonly activeCount = computed(() => this.rows().filter((c) => c.active).length);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listCompanies().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiError(err, 'Could not load companies.'));
        this.loading.set(false);
      },
    });
  }

  /** Opens a company's profile under its row; a second click closes it. */
  expand(c: AdminCompany): void {
    if (this.expanded() === c.id) {
      this.expanded.set(null);
      return;
    }
    this.expanded.set(c.id);
    this.detail.set(null);
    this.api.getCompany(c.id).subscribe({
      next: (d) => this.detail.set(d),
      error: (err) => this.error.set(apiError(err, 'Could not load that company.')),
    });
  }

  toggle(c: AdminCompany): void {
    const turningOff = c.active;
    if (turningOff && !confirm(`Switch off ${c.name}? Its account is signed out at once and cannot sign in `
        + 'until you switch it back on. Its tenders and profile are kept.')) {
      return;
    }
    this.busy.set(c.id);
    this.error.set(null);
    this.api.setCompanyActive(c.id, !c.active).subscribe({
      next: (updated) => {
        this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
        this.busy.set(null);
      },
      error: (err) => {
        this.error.set(apiError(err, 'Could not change that company.'));
        this.busy.set(null);
      },
    });
  }
}
