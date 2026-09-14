import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

/**
 * Who you are signed in as, and the way out.
 *
 * Names the company for a company account, so which tenant you are looking at stays
 * answerable at a glance; for TenderSense staff it says so, with the email.
 */
@Component({
  selector: 'ts-account-menu',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (user(); as u) {
      <div class="wrap">
        <button type="button" class="trigger" [class.open]="open()" (click)="toggle()"
                [attr.aria-expanded]="open()" aria-haspopup="menu">
          <span class="av" [class.staff]="!company()">{{ initials(name()) }}</span>
          <span class="who">
            <span class="nm">{{ name() }}</span>
            <span class="em">{{ u.email }}</span>
          </span>
          <span class="cv" aria-hidden="true">▾</span>
        </button>

        @if (open()) {
          <div class="menu" role="menu" animate.enter="opening" animate.leave="closing">
            <div class="mh">
              <span class="mn">{{ name() }}</span>
              @if (isAdmin()) {
                <span class="tag admin">admin</span>
              }
              @if (company()?.demonstration) {
                <span class="tag demo">demo tenant</span>
              }
              <span class="me">{{ u.email }}</span>
            </div>
            @if (company()) {
              <a class="mi" role="menuitem" routerLink="/profile" (click)="open.set(false)">
                Profile
              </a>
            }
            @if (isAdmin()) {
              <a class="mi" role="menuitem" routerLink="/admin/pipeline" (click)="open.set(false)">
                Pipeline activity
              </a>
            }
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
    .trigger { transition: border-color var(--t-fast) ease, background var(--t-fast) ease; }
    .trigger:hover, .trigger.open { border-color: var(--accent); }
    .cv { transition: transform var(--t) var(--ease-out); }
    .trigger.open .cv { transform: rotate(180deg); }
    .trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

    .av {
      display: grid; place-items: center;
      width: 28px; height: 28px; border-radius: var(--r-sm);
      background: var(--accent); color: #fff;
      font-size: 10.5px; font-weight: 800; flex: none;
    }
    .av.staff { background: var(--ink-2); }
    .who { display: flex; flex-direction: column; line-height: 1.2; min-width: 0; }
    .nm, .em { max-width: 190px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .nm { font-size: 12.5px; font-weight: 700; }
    .em { font-size: 10.5px; color: var(--ink-4); }
    .cv { font-size: 10px; color: var(--ink-4); }

    .menu {
      position: absolute; top: calc(100% + 8px); right: 0; z-index: 40;
      width: 260px; padding: 6px;
      background: var(--surface); border: 1px solid var(--line);
      border-radius: var(--r); box-shadow: var(--shadow-pop);
      transform-origin: top right;
    }
    .menu.opening { animation: pop-in var(--t-fast) var(--ease-out) both; }
    .menu.closing { animation: pop-out var(--t-fast) var(--ease-in) both; }
    .mh {
      display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
      padding: 10px 12px 9px; margin-bottom: 5px;
      border-bottom: 1px solid var(--line-soft);
    }
    .mn { font-size: 13px; font-weight: 800; }
    .me { flex-basis: 100%; font-size: 11.5px; color: var(--ink-3); overflow-wrap: anywhere; }
    .tag {
      font-size: 9px; font-weight: 700; letter-spacing: 0.3px; text-transform: uppercase;
      border-radius: var(--r-sm); padding: 1px 5px; border: 1px solid;
    }
    .tag.demo { color: var(--warn); background: var(--warn-wash); border-color: var(--warn-edge); }
    .tag.admin { color: var(--accent-ink); background: var(--accent-wash); border-color: var(--accent-wash); }

    .mi {
      display: block; width: 100%; padding: 9px 12px;
      border: 0; border-radius: var(--r-sm); background: transparent;
      color: var(--ink-2); font-family: inherit; font-size: 13px; font-weight: 600;
      text-align: left; cursor: pointer;
    }
    .mi { transition: background var(--t-fast) ease, color var(--t-fast) ease; }
    .mi:hover { background: var(--surface-3); text-decoration: none; }
    .mi.hot { color: var(--bad); }
    .mi:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
  `,
})
export class AccountMenu {
  private readonly auth = inject(AuthService);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly user = this.auth.user;
  readonly company = this.auth.company;
  readonly isAdmin = this.auth.isAdmin;
  readonly name = computed(() => this.company()?.name ?? 'TenderSense admin');
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
