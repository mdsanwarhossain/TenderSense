import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
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

  /** Tracks the active URL so the rail and the top bar agree on where we are. */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /**
   * The top bar names the screen; the screen itself states what is on it. Derived
   * from the URL rather than pushed by each feature, so a feature never has to know
   * the shell exists.
   */
  readonly section = computed<{ title: string; sub: string }>(() => {
    const url = this.url();
    const org = this.orgService.current()?.name;
    if (url.startsWith('/tenders/')) {
      return { title: 'Tender detail', sub: 'Opened from the morning shortlist' };
    }
    if (url.startsWith('/benchmark')) {
      return { title: 'Benchmark', sub: 'Semantic matching against the keyword baseline' };
    }
    if (url.startsWith('/profile')) {
      return { title: 'Capability profile', sub: org ?? 'What every tender is compared against' };
    }
    if (url.startsWith('/pipeline')) {
      return { title: 'Collection pipeline', sub: 'Asia/Dhaka · one job at a time' };
    }
    return { title: 'Morning shortlist', sub: `${this.todayLabel} · e-GP Bangladesh + World Bank` };
  });

  private readonly todayLabel = new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());

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
