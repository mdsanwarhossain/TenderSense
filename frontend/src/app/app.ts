import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './core/services/auth.service';
import { AccountMenu } from './shared/account-menu';
import { NotificationBell } from './shared/notification-bell';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AccountMenu, NotificationBell],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /**
   * The signed-in company, or null on /login and /signup. The shell — rail, top bar —
   * renders only when this is set, so the auth pages get the full window.
   */
  readonly company = this.auth.company;

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
    const org = this.company()?.name;
    if (url.startsWith('/tenders/')) {
      return { title: 'Tender detail', sub: 'Opened from the tender list' };
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
    return { title: 'Tender list', sub: `${this.todayLabel} · e-GP BD · World Bank · UNGM · IsDB` };
  });

  private readonly todayLabel = new Intl.DateTimeFormat('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());

  constructor() {
    // The route guard resolves this before any protected screen renders; calling it
    // here as well means a hard refresh on /login does not flash the shell first.
    void this.auth.ensureLoaded();
  }
}
