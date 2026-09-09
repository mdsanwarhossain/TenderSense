import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { OrgService } from '../core/services/org.service';
import { Organisation } from '../core/models/tender.models';

/**
 * Switches the company the whole app is acting for.
 *
 * This is the general-product claim made visible in one click: the same scraped
 * corpus, a different profile, a completely different shortlist. The sector chips
 * are shown because they explain *why* the two lists share nothing — each company
 * is gated to the sectors it subscribes to.
 */
@Component({
  selector: 'ts-org-switcher',
  standalone: true,
  template: `
    @if (orgs().length) {
      <div class="switcher">
        <button
          type="button"
          class="trigger"
          [class.open]="open()"
          (click)="toggle()"
          [attr.aria-expanded]="open()"
          aria-haspopup="listbox"
        >
          <span class="mark">{{ initials(current()) }}</span>
          <span class="labels">
            <span class="name">{{ current()?.name }}</span>
            <span class="meta">{{ current()?.sectors?.length }} sectors</span>
          </span>
          <span class="caret" aria-hidden="true">▾</span>
        </button>

        @if (open()) {
          <div class="menu" role="listbox">
            <p class="menu-head">Acting as</p>
            @for (o of orgs(); track o.id) {
              <button
                type="button"
                role="option"
                class="option"
                [class.selected]="o.id === current()?.id"
                [attr.aria-selected]="o.id === current()?.id"
                (click)="choose(o)"
              >
                <span class="row">
                  <span class="opt-name">{{ o.name }}</span>
                  @if (o.demonstration) {
                    <span class="demo">demo tenant</span>
                  }
                </span>
                @if (o.description) {
                  <span class="desc">{{ o.description }}</span>
                }
                <span class="sectors">
                  @for (s of o.sectors; track s) {
                    <span class="sector">{{ pretty(s) }}</span>
                  }
                </span>
              </button>
            }
            <p class="menu-foot">
              Every company reads the same corpus and is scored against its own profile.
            </p>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .switcher { position: relative; }

    .trigger {
      display: flex; align-items: center; gap: 10px;
      height: 42px; padding: 0 12px 0 8px;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-pill);
      color: var(--ink);
      cursor: pointer;
      font: inherit;
      text-align: left;
    }
    .trigger:hover, .trigger.open { border-color: var(--accent); }

    .mark {
      display: flex; align-items: center; justify-content: center;
      width: 28px; height: 28px; border-radius: 50%;
      background: var(--accent-wash); color: var(--accent-ink);
      font-size: 11px; font-weight: 800;
      flex: none;
    }
    .labels { display: flex; flex-direction: column; line-height: 1.25; }
    .name { font-size: 12.5px; font-weight: 700; color: var(--ink); }
    .meta { font-size: 10.5px; color: var(--ink-4); }
    .caret { font-size: 9px; color: var(--ink-4); margin-left: 2px; }

    .menu {
      position: absolute; top: calc(100% + 8px); right: 0; z-index: 40;
      width: 340px;
      padding: 8px;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-lg);
      box-shadow: var(--shadow-pop);
    }
    .menu-head, .menu-foot {
      margin: 0; padding: 8px 10px 6px;
      font-size: 10px; font-weight: 700; letter-spacing: 0.9px; text-transform: uppercase;
      color: var(--ink-4);
    }
    .menu-foot {
      text-transform: none; letter-spacing: 0; font-weight: 500;
      font-size: 11.5px; color: var(--ink-3); line-height: 1.5;
      border-top: 1px solid var(--line-soft); margin-top: 6px; padding-top: 10px;
    }

    .option {
      display: block; width: 100%;
      padding: 10px;
      background: transparent; border: 0; border-radius: var(--r);
      cursor: pointer; font: inherit; text-align: left;
    }
    .option:hover { background: var(--surface-3); }
    .option.selected { background: var(--accent-soft); }

    .row { display: flex; align-items: center; gap: 8px; }
    .opt-name { font-size: 13px; font-weight: 700; color: var(--ink); }
    .demo {
      font-size: 10px; font-weight: 700;
      color: var(--warn); background: var(--warn-wash);
      border-radius: var(--r-pill); padding: 1px 8px;
    }
    .desc { display: block; margin-top: 3px; font-size: 11.5px; color: var(--ink-3); }
    .sectors { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 8px; }
    .sector {
      font-size: 10.5px; font-weight: 600; color: var(--ink-3);
      background: var(--surface-3); border: 1px solid var(--line);
      border-radius: var(--r-pill); padding: 2px 8px;
    }
  `,
})
export class OrgSwitcher {
  private readonly orgService = inject(OrgService);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly orgs = this.orgService.organisations;
  readonly current = this.orgService.current;
  readonly open = signal(false);

  /** Emits nothing: the shell watches {@link OrgService.currentId} and remounts. */
  toggle(): void {
    this.open.update((v) => !v);
  }

  choose(org: Organisation): void {
    this.open.set(false);
    if (org.id !== this.current()?.id) {
      this.orgService.select(org.id);
    }
  }

  initials(org: Organisation | null): string {
    if (!org) return '··';
    return org.name
      .split(/\s+/)
      .filter((w) => /[A-Za-z]/.test(w))
      .slice(0, 2)
      .map((w) => w[0]!.toUpperCase())
      .join('');
  }

  /** IT_SERVICES reads as noise in a chip; "IT services" does not. */
  pretty(sector: string): string {
    const words = sector.toLowerCase().split('_');
    const head = words[0] === 'it' ? 'IT' : words[0][0].toUpperCase() + words[0].slice(1);
    return [head, ...words.slice(1)].join(' ');
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}
