import { Component, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { OrgService } from './core/services/org.service';
import { OrgSwitcher } from './shared/org-switcher';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, OrgSwitcher],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly orgService = inject(OrgService);
  private readonly router = inject(Router);

  /**
   * Gates the router outlet so that switching company tears the current screen down
   * and builds it again. Every feature loads its data in its constructor, so a
   * remount is what makes the whole app refetch under the new `X-Org-Id` — without
   * a page reload, and without each screen having to subscribe to the selection.
   */
  readonly mounted = signal(true);

  private lastOrgId = this.orgService.currentId();

  constructor() {
    this.orgService.load();

    effect(() => {
      const id = this.orgService.currentId();
      untracked(() => {
        if (id === this.lastOrgId) {
          return;
        }
        this.lastOrgId = id;
        // A tender detail is scoped to one company; the other may not be able to
        // see that tender at all. Everything else is a view of the same corpus and
        // is meaningful for either, so it stays put and reloads in place.
        if (this.router.url.startsWith('/tenders/')) {
          this.router.navigate(['/shortlist']);
          return;
        }
        this.remount();
      });
    });
  }

  private remount(): void {
    this.mounted.set(false);
    // One tick with the outlet empty: long enough for Angular to destroy the
    // component, short enough that nothing flickers.
    setTimeout(() => this.mounted.set(true));
  }
}
