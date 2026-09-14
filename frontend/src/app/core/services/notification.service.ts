import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, interval, of, startWith, switchMap } from 'rxjs';
import { NotificationItem } from '../models/tender.models';
import { ApiService } from './api.service';

/** How often the bell checks for new matches while a screen is open. */
const POLL_MS = 30_000;

/** How many rows the dropdown shows -- older ones are still counted, just not listed. */
const PANEL_SIZE = 8;

/**
 * State behind the notification bell: the live unread count and the short list the
 * dropdown renders.
 *
 * <p>Polling is started by {@link NotificationBell} and bound to that component's own
 * lifetime, not this service's -- the bell only exists in the DOM while a company is
 * signed in (see app.html), so signing out tears the poll down for free and the next
 * sign-in starts a clean one instead of two polls racing.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(ApiService);

  readonly unreadCount = signal(0);
  readonly items = signal<NotificationItem[]>([]);
  readonly loading = signal(false);

  startPolling(destroyRef: DestroyRef): void {
    this.unreadCount.set(0);
    this.items.set([]);
    this.refresh();

    interval(POLL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.api.getUnreadNotificationCount().pipe(
          // A transient failure should not spin the badge down to zero -- keep showing
          // the last known count and let the next tick recover.
          catchError(() => of(this.unreadCount())),
        )),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((count) => this.unreadCount.set(count));
  }

  /** Refetches the panel's rows. Called on open, so the count and the list never drift apart. */
  refresh(): void {
    this.loading.set(true);
    this.api.listNotifications({ size: PANEL_SIZE }).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  /** Optimistic: the row and the badge update immediately, the API call trails behind. */
  markRead(id: number): void {
    const item = this.items().find((n) => n.id === id);
    if (!item || item.read) {
      return;
    }
    this.items.update((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
    this.unreadCount.update((c) => Math.max(0, c - 1));
    this.api.markNotificationRead(id).subscribe({ error: () => this.refresh() });
  }

  markAllRead(): void {
    if (this.unreadCount() === 0) {
      return;
    }
    this.items.update((list) => list.map((n) => ({ ...n, read: true })));
    this.unreadCount.set(0);
    this.api.markAllNotificationsRead().subscribe({ error: () => this.refresh() });
  }
}
