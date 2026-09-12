import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';

/** One choice in a {@link Select}. */
export interface SelectOption {
  /** '' is the "everything" choice, e.g. All grades. */
  value: string;
  label: string;
  /** A short second line in the list, e.g. "80–100% match". */
  hint?: string;
  /** A coloured dot before the label (a source portal). */
  dot?: string;
  /** A small letter tile before the label (a grade), in the same colours as the table. */
  tile?: { text: string; bg: string; fg: string };
}

let nextId = 0;

/**
 * The app's dropdown: a pill that shows what is chosen, opening a card of options.
 *
 * Replaces the native select, which cannot show the grade and source colours the table
 * uses. Keyboard works as a select does: arrows move, Enter chooses, Escape closes, and
 * focus never leaves the button (the list is announced through aria-activedescendant).
 */
@Component({
  selector: 'ts-select',
  standalone: true,
  template: `
    <button type="button" class="trigger" [class.open]="open()" [class.set]="marksFilter() && value() !== ''"
            aria-haspopup="listbox" [attr.aria-expanded]="open()" [attr.aria-controls]="id + '-list'"
            [attr.aria-activedescendant]="open() ? id + '-opt-' + active() : null"
            [attr.aria-label]="label() + ': ' + current().label"
            (click)="toggle()" (keydown)="onKey($event)">
      <span class="lbl">{{ label() }}</span>
      <span class="cur">
        @if (current().tile; as t) {
          <span class="tile" [style.background]="t.bg" [style.color]="t.fg">{{ t.text }}</span>
        } @else if (current().dot) {
          <span class="dot" [style.background]="current().dot"></span>
        }
        {{ current().label }}
      </span>
      <svg class="chev" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="m6 9 6 6 6-6" />
      </svg>
    </button>

    @if (open()) {
      <ul class="menu" [class.up]="up()" role="listbox" [id]="id + '-list'" [attr.aria-label]="label()"
          animate.enter="opening" animate.leave="closing">
        @for (o of options(); track o.value; let i = $index) {
          <li role="option" [id]="id + '-opt-' + i" [attr.aria-selected]="o.value === value()"
              [class.active]="i === active()" [class.selected]="o.value === value()"
              (mouseenter)="active.set(i)" (mousedown)="$event.preventDefault()" (click)="choose(o)">
            @if (o.tile; as t) {
              <span class="tile" [style.background]="t.bg" [style.color]="t.fg">{{ t.text }}</span>
            } @else if (o.dot) {
              <span class="dot" [style.background]="o.dot"></span>
            } @else {
              <span class="blank"></span>
            }
            <span class="text">
              <span class="ol">{{ o.label }}</span>
              @if (o.hint) { <span class="oh">{{ o.hint }}</span> }
            </span>
            @if (o.value === value()) {
              <svg class="check" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            }
          </li>
        }
      </ul>
    }`,
  styles: [`
    :host { position: relative; display: inline-flex; }

    .trigger {
      display: inline-flex; align-items: center; gap: 9px;
      height: 40px; padding: 0 14px 0 16px;
      border: 1px solid var(--line); border-radius: var(--r-pill);
      background: var(--surface); color: var(--ink-2);
      font: inherit; font-size: 13px; cursor: pointer;
      box-shadow: var(--btn-edge);
      transition: border-color 0.12s ease, background 0.12s ease, box-shadow 0.12s ease, transform 0.1s ease;
    }
    .trigger:hover { border-color: var(--accent); box-shadow: var(--btn-edge-hover); transform: translateY(-1px); }
    .trigger:active { transform: translateY(1px); }
    .trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
    /* Open: sits pressed in, like a toggle that is on. */
    .trigger.open, .trigger.open:hover { border-color: var(--accent); box-shadow: var(--btn-press-soft); transform: none; }
    /* A filter is on: the pill says so before you read it. */
    .trigger.set { border-color: var(--accent); background: var(--accent-soft); }
    .lbl {
      font-size: 10px; font-weight: 700; letter-spacing: 0.8px; text-transform: uppercase;
      color: var(--ink-4);
    }
    .cur { display: inline-flex; align-items: center; gap: 7px; font-weight: 700; color: var(--ink); white-space: nowrap; }
    .chev { color: var(--ink-3); transition: transform 0.15s ease; }
    .trigger.open .chev { transform: rotate(180deg); }

    .menu {
      position: absolute; top: calc(100% + 8px); left: 0; z-index: 40;
      min-width: 100%; max-height: 340px; overflow-y: auto;
      margin: 0; padding: 6px; list-style: none;
      background: var(--surface); border: 1px solid var(--line); border-radius: var(--r);
      box-shadow: var(--shadow-pop);
      transform-origin: top center;
    }
    /* The list opens and shuts; Angular keeps it in the page until the closing run ends. */
    .menu.opening { animation: pop-in var(--t-fast) var(--ease-out) both; }
    .menu.closing { animation: pop-out var(--t-fast) var(--ease-in) both; }
    /* Opens upwards: for a dropdown at the bottom of a card that clips its overflow. */
    .menu.up { top: auto; bottom: calc(100% + 8px); transform-origin: bottom center; }
    .menu.up.opening { animation-name: pop-in-up; }
    .menu.up.closing { animation-name: pop-out-up; }
    li {
      display: flex; align-items: center; gap: 10px;
      padding: 8px 10px; border-radius: var(--r-sm);
      font-size: 13px; color: var(--ink-2); cursor: pointer; white-space: nowrap;
    }
    li.active { background: var(--surface-3); }
    li.selected .ol { color: var(--accent-ink); font-weight: 700; }
    .text { display: flex; flex-direction: column; gap: 1px; flex: 1; }
    .ol { font-weight: 600; }
    .oh { font-size: 11.5px; color: var(--ink-4); }
    .check { color: var(--accent); flex: none; }

    .dot { width: 9px; height: 9px; border-radius: 50%; flex: none; }
    .tile {
      display: inline-grid; place-items: center; flex: none;
      width: 24px; height: 22px; border-radius: 6px;
      font-size: 11.5px; font-weight: 800;
    }
    .cur .tile { width: 22px; height: 20px; font-size: 11px; }
    .blank { width: 24px; flex: none; }

    li { transition: background var(--t-fast) ease; }

    @media (prefers-reduced-motion: reduce) {
      .menu.opening, .menu.closing { animation: none; }
      .chev, .trigger { transition: none; }
    }
  `],
})
export class Select {
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly label = input.required<string>();
  readonly options = input.required<SelectOption[]>();
  readonly value = input<string>('');
  /** Open the list above the button instead of below it. */
  readonly up = input(false);
  /** Tint the pill while a value is chosen -- right for a filter, wrong for a setting like page size. */
  readonly marksFilter = input(true);
  readonly changed = output<string>();

  readonly id = `ts-select-${nextId++}`;
  readonly open = signal(false);
  /** The highlighted option while the list is open. */
  readonly active = signal(0);

  readonly current = computed(() =>
    this.options().find((o) => o.value === this.value()) ?? this.options()[0]);

  toggle(): void {
    this.open() ? this.close() : this.show();
  }

  choose(o: SelectOption): void {
    this.close();
    if (o.value !== this.value()) {
      this.changed.emit(o.value);
    }
  }

  onKey(e: KeyboardEvent): void {
    const last = this.options().length - 1;
    if (!this.open()) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        this.show();
      }
      return;
    }
    switch (e.key) {
      case 'ArrowDown': e.preventDefault(); this.active.update((i) => Math.min(last, i + 1)); break;
      case 'ArrowUp': e.preventDefault(); this.active.update((i) => Math.max(0, i - 1)); break;
      case 'Home': e.preventDefault(); this.active.set(0); break;
      case 'End': e.preventDefault(); this.active.set(last); break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        this.choose(this.options()[this.active()]);
        break;
      case 'Escape': e.preventDefault(); this.close(); break;
      case 'Tab': this.close(); break;
    }
  }

  /** A click anywhere else closes the list, as a native select does. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(e: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(e.target as Node)) {
      this.close();
    }
  }

  private show(): void {
    this.active.set(Math.max(0, this.options().findIndex((o) => o.value === this.value())));
    this.open.set(true);
  }

  private close(): void {
    this.open.set(false);
  }
}
