import { Component, computed, input, output } from '@angular/core';
import { Select, SelectOption } from './select';

/**
 * Rows per page on the left, page numbers on the right. The foot of every paged table,
 * so the tender list, the pipeline runs and the admin tables page the same way.
 *
 * `page` is 0-based. The parent owns the state and fetches; this only asks for changes.
 */
@Component({
  selector: 'ts-pager',
  standalone: true,
  imports: [Select],
  template: `
    <footer class="pager">
      <div class="pager-size">
        <!-- Opens upwards: a card clips anything that hangs below it. -->
        <ts-select label="Rows" [options]="sizeOptions()" [value]="'' + size()" [up]="true" [marksFilter]="false"
                   (changed)="onSize($event)" />
        <span class="range">{{ rangeStart() }}–{{ rangeEnd() }} of {{ total() }}</span>
      </div>
      <nav class="pages" aria-label="Pages">
        <button type="button" class="pg arrow" (click)="go(page() - 1)" [disabled]="page() === 0"
                aria-label="Previous page">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6" /></svg>
        </button>
        @for (p of pages(); track $index) {
          @if (p === null) {
            <span class="pg gap" aria-hidden="true">…</span>
          } @else {
            <button type="button" class="pg" [class.on]="p === page()"
                    [attr.aria-current]="p === page() ? 'page' : null"
                    [attr.aria-label]="'Page ' + (p + 1)" (click)="go(p)">{{ p + 1 }}</button>
          }
        }
        <button type="button" class="pg arrow" (click)="go(page() + 1)"
                [disabled]="page() >= pageCount() - 1" aria-label="Next page">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6" /></svg>
        </button>
      </nav>
    </footer>
  `,
  styles: `
    :host { display: block; }
    .pager-size { display: flex; align-items: center; gap: 14px; }
    .pager-size .range { font-size: 12.5px; color: var(--ink-3); font-variant-numeric: tabular-nums; }
    .pages { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }

    /* Page buttons read as buttons: outlined with a bottom edge, lifting on hover. */
    .pg {
      display: inline-grid; place-items: center;
      min-width: 36px; height: 36px; padding: 0 10px;
      border: 1px solid var(--line); border-radius: var(--r-pill);
      background: var(--surface); box-shadow: var(--btn-edge);
      font: inherit; font-size: 13px; font-weight: 700; font-variant-numeric: tabular-nums;
      color: var(--ink-2); cursor: pointer;
      transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease, box-shadow 0.12s ease, transform 0.1s ease;
    }
    .pg:hover:not(:disabled):not(.on):not(.gap) {
      border-color: var(--accent); color: var(--ink); box-shadow: var(--btn-edge-hover); transform: translateY(-1px);
    }
    .pg:active:not(:disabled):not(.on):not(.gap) { transform: translateY(1px); box-shadow: var(--btn-press-soft); }
    .pg:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
    /* The current page is a filled purple button. */
    .pg.on { background: var(--accent); border-color: var(--accent); color: #fff; box-shadow: var(--btn-lift); cursor: default; }
    .pg:disabled { color: var(--ink-4); opacity: 0.45; cursor: not-allowed; box-shadow: none; }
    .pg.gap {
      min-width: 22px; padding: 0; border-color: transparent; background: none; box-shadow: none;
      color: var(--ink-4); cursor: default;
    }
    @media (prefers-reduced-motion: reduce) { .pg { transition: none; } .pg:hover, .pg:active { transform: none; } }
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  readonly sizes = input<number[]>([10, 25, 50, 100]);

  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();

  readonly sizeOptions = computed<SelectOption[]>(() =>
    this.sizes().map((n) => ({ value: String(n), label: `${n} per page` })));

  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));

  /** "26–50 of 316": which rows of the whole list this page holds. */
  readonly rangeStart = computed(() => (this.total() === 0 ? 0 : this.page() * this.size() + 1));
  readonly rangeEnd = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));

  /**
   * Page buttons, 0-based, with null for a "…" gap. Always the first and last page and
   * the ones either side of the current page, so 13 pages read 1 2 3 4 5 … 13 at the
   * start and 1 … 6 7 8 … 13 in the middle.
   */
  readonly pages = computed<(number | null)[]>(() => {
    const n = this.pageCount();
    const c = this.page();
    if (n <= 7) return Array.from({ length: n }, (_, i) => i);
    const shown = new Set([0, n - 1, c - 1, c, c + 1]);
    if (c <= 3) [1, 2, 3, 4].forEach((i) => shown.add(i));
    if (c >= n - 4) [n - 5, n - 4, n - 3, n - 2].forEach((i) => shown.add(i));
    const sorted = [...shown].filter((i) => i >= 0 && i < n).sort((a, b) => a - b);
    const out: (number | null)[] = [];
    sorted.forEach((p, i) => {
      if (i > 0 && p - sorted[i - 1] > 1) out.push(null);
      out.push(p);
    });
    return out;
  });

  go(p: number): void {
    if (p < 0 || p >= this.pageCount() || p === this.page()) return;
    this.pageChange.emit(p);
  }

  /** A new page size; the parent starts again from the first page. */
  onSize(value: string): void {
    this.sizeChange.emit(Number(value));
  }
}
