import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { AdminTender, SOURCE_LABELS, SOURCE_OPTIONS, SourcePortal } from '../../core/models/tender.models';
import { Pager } from '../../shared/pager';
import { Select, SelectOption } from '../../shared/select';
import { apiError, timeAgo } from '../../shared/format';

/** Same dots as the tender list's source column, so a portal looks the same everywhere. */
const SOURCE_DOTS: Record<SourcePortal, string> = {
  EGP_BANGLADESH: '#5b3df5', WORLD_BANK: '#2f8fbe', UNGM: '#c9781f', ISDB: '#1a9e6b', BRAC: '#d81b7a',
};

/** What the local model did with a tender, in the words the AI queue card uses. */
const AI_LABELS: Record<string, string> = {
  DONE: 'Read', SKIPPED: 'Skipped', FAILED: 'Could not read',
};

/**
 * Every tender collected, for TenderSense staff.
 *
 * <p>No score, no grade, no saved or submitted buttons: those belong to a company, and an
 * admin account has none. This answers "what is in the corpus and what did the model make
 * of it", which the company screens cannot.
 */
@Component({
  selector: 'ts-admin-tenders',
  standalone: true,
  imports: [DatePipe, DecimalPipe, Pager, Select],
  templateUrl: './tenders.html',
  styleUrls: ['./admin.css', './tenders.css'],
})
export class AdminTenders {
  private readonly api = inject(ApiService);

  readonly rows = signal<AdminTender[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly size = signal(25);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly source = signal<SourcePortal | null>(null);
  readonly includeClosed = signal(true);
  readonly search = signal('');

  readonly sourceOptions: SelectOption[] = [
    { value: '', label: 'All sources' },
    ...SOURCE_OPTIONS.map((s) => ({ value: s, label: SOURCE_LABELS[s], dot: SOURCE_DOTS[s] })),
  ];

  readonly timeAgo = timeAgo;
  readonly shown = computed(() => this.rows().length);

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listAdminTenders({
      page: this.page(),
      size: this.size(),
      source: this.source() ?? undefined,
      includeClosed: this.includeClosed(),
      search: this.search(),
    }).subscribe({
      next: (res) => {
        this.rows.set(res.content);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiError(err, 'Could not load the tender list.'));
        this.loading.set(false);
      },
    });
  }

  sourceLabel(source: SourcePortal): string {
    return SOURCE_LABELS[source];
  }

  dot(source: SourcePortal): string {
    return SOURCE_DOTS[source];
  }

  aiLabel(status: string | null): string {
    return status ? AI_LABELS[status] ?? status : 'Not read yet';
  }

  setSource(value: string): void {
    this.source.set((value || null) as SourcePortal | null);
    this.reload();
  }

  toggleClosed(): void {
    this.includeClosed.update((v) => !v);
    this.reload();
  }

  /** Searches the whole corpus, not the page on screen, so it waits for typing to settle. */
  typed(value: string): void {
    this.search.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.reload(), 350);
  }

  goTo(page: number): void {
    this.page.set(page);
    this.load();
  }

  setSize(size: number): void {
    this.size.set(size);
    this.reload();
  }

  private reload(): void {
    this.page.set(0);
    this.load();
  }
}
