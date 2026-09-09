import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

/**
 * Who you are signed in as, and the way out.
 *
 * Occupies the slot the company switcher held, and keeps its one genuinely useful
 * property: naming the company, so which tenant you are looking at stays answerable
 * at a glance.
 */
@Component({
  selector: 'ts-account-menu',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (company(); as org) {
      <div class="wrap">
        <button type="button" class="trigger" [class.open]="open()" (click)="toggle()"
                [attr.aria-expanded]="open()" aria-haspopup="menu">
          <span class="av">{{ initials(org.name) }}</span>
          <span class="who">
            <span class="nm">{{ org.name }}</span>
            <span class="em">{{ org.sectors.length }} sectors</span>
          </span>
          <span class="cv" aria-hidden="true">▾</span>
        </button>

        @if (open()) {
          <div class="menu" role="menu">
            <div class="mh">
              <span class="mn">{{ org.name }}</span>
              @if (org.demonstration) {
                <span class="demo">demo tenant</span>
              }
            </div>
            <a class="mi" role="menuitem" routerLink="/profile" (click)="open.set(false)">
              Capability profile
            </a>
            <a class="mi" role="menuitem" routerLink="/pipeline" (click)="open.set(false)">
              Pipeline activity
            </a>
            <button type="button" class="mi hot" role="menuitem" (click)="signOut()">
              Sign out
            </button>
          </div>
        }
      </div>
    }
  `,
  styles: `
    .wrap { position: relative; }

    .trigger {
      display: flex; align-items: center; gap: 9px;
      padding: 5px 12px 5px 6px;
      border: 1px solid var(--line); border-radius: var(--r-pill);
      background: var(--surface); color: var(--ink);
      font-family: inherit; cursor: pointer; text-align: left;
    }
    .trigger:hover, .trigger.open { border-color: var(--accent); }
    .trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

    .av {
      display: grid; place-items: center;
      width: 28px; height: 28px; border-radius: var(--r-sm);
      background: var(--accent); color: #fff;
      font-size: 10.5px; font-weight: 800; flex: none;
    }
    .who { display: flex; flex-direction: column; line-height: 1.2; min-width: 0; }
    .nm {
      font-size: 12.5px; font-weight: 700; max-width: 190px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .em { font-size: 10.5px; color: var(--ink-4); }
    .cv { font-size: 10px; color: var(--ink-4); }

    .menu {
      position: absolute; top: calc(100% + 8px); right: 0; z-index: 40;
      width: 250px; padding: 6px;
      background: var(--surface); border: 1px solid var(--line);
      border-radius: var(--r); box-shadow: var(--shadow-pop);
    }
    .mh {
      display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
      padding: 10px 12px 9px; margin-bottom: 5px;
      border-bottom: 1px solid var(--line-soft);
    }
    .mn { font-size: 13px; font-weight: 800; }
    .demo {
      font-size: 9px; font-weight: 700; letter-spacing: 0.3px; text-transform: uppercase;
      color: var(--warn); background: var(--warn-wash);
      border: 1px solid var(--warn-edge); border-radius: var(--r-sm); padding: 1px 5px;
    }

    .mi {
      display: block; width: 100%; padding: 9px 12px;
      border: 0; border-radius: var(--r-sm); background: transparent;
      color: var(--ink-2); font-family: inherit; font-size: 13px; font-weight: 600;
      text-align: left; cursor: pointer;
    }
    .mi:hover { background: var(--surface-3); text-decoration: none; }
    .mi.hot { color: var(--bad); }
    .mi:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
  `,
})
export class AccountMenu {
  private readonly auth = inject(AuthService);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly company = this.auth.company;
  readonly open = signal(false);

  toggle(): void {
    this.open.update((v) => !v);
  }

  signOut(): void {
    this.open.set(false);
    void this.auth.logout();
  }

  initials(name: string): string {
    return name
      .split(/\s+/)
      .filter((w) => /[A-Za-z]/.test(w))
      .slice(0, 2)
      .map((w) => w[0]!.toUpperCase())
      .join('');
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
