import { Component, DestroyRef, ElementRef, HostListener, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationService } from '../core/services/notification.service';
import { GradeBadge } from './grade-badge';

/**
 * The bell in the top bar: a live red count of newly-matched tenders, opening onto the
 * most recent few. Clicking a row marks it read and opens the tender it matched --
 * the same destination the shortlist opens.
 */
@Component({
  selector: 'ts-notification-bell',
  standalone: true,
  imports: [RouterLink, GradeBadge],
  template: `
    <div class="wrap">
      <button type="button" class="trigger" [class.open]="open()" (click)="toggle()"
              [attr.aria-expanded]="open()" aria-haspopup="menu" aria-label="Notifications">
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        @if (notifications.unreadCount() > 0) {
          <span class="badge">{{ notifications.unreadCount() > 99 ? '99+' : notifications.unreadCount() }}</span>
        }
      </button>

      @if (open()) {
        <div class="panel" role="menu">
          <div class="ph">
            <span class="pt">Notifications</span>
            @if (notifications.unreadCount() > 0) {
              <button type="button" class="mark-all" (click)="notifications.markAllRead()">
                Mark all read
              </button>
            }
          </div>

          @if (notifications.loading()) {
            <div class="empty">Loading…</div>
          } @else if (notifications.items().length === 0) {
            <div class="empty">
              <span class="et">No new matches yet</span>
              <span class="es">You'll see it here the moment a tender matches your profile.</span>
            </div>
          } @else {
            <div class="list">
              @for (n of notifications.items(); track n.id) {
                <a class="item" [class.unread]="!n.read" role="menuitem"
                   [routerLink]="['/tenders', n.tenderId]" (click)="select(n.id)">
                  <ts-grade-badge [grade]="n.grade" />
                  <span class="body">
                    <span class="ttl">{{ n.tenderTitle ?? 'Untitled tender' }}</span>
                    <span class="time">{{ fit(n.score) }} fit · {{ timeAgo(n.createdAt) }}</span>
                  </span>
                  @if (!n.read) {
                    <span class="dot" aria-hidden="true"></span>
                  }
                </a>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .wrap { position: relative; }

    .trigger {
      position: relative;
      display: grid; place-items: center;
      width: 36px; height: 36px;
      border: 1px solid var(--line); border-radius: var(--r-pill);
      background: var(--surface); color: var(--ink-2);
      cursor: pointer;
    }
    .trigger:hover, .trigger.open { border-color: var(--accent); color: var(--ink); }
    .trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

    .badge {
      position: absolute; top: -4px; right: -4px;
      display: grid; place-items: center;
      min-width: 17px; height: 17px; padding: 0 4px;
      border-radius: var(--r-pill); border: 2px solid var(--surface);
      background: var(--bad); color: #fff;
      font-size: 9.5px; font-weight: 800; line-height: 1;
    }

    .panel {
      position: absolute; top: calc(100% + 8px); right: -6px; z-index: 40;
      width: 340px; max-width: calc(100vw - 32px);
      background: var(--surface); border: 1px solid var(--line);
      border-radius: var(--r); box-shadow: var(--shadow-pop);
      overflow: hidden;
    }
    .ph {
      display: flex; align-items: center; justify-content: space-between; gap: 8px;
      padding: 12px 14px; border-bottom: 1px solid var(--line-soft);
    }
    .pt { font-size: 13px; font-weight: 800; color: var(--ink); }
    .mark-all {
      border: 0; background: transparent; color: var(--accent-ink);
      font-family: inherit; font-size: 11.5px; font-weight: 700; cursor: pointer; padding: 2px;
    }
    .mark-all:hover { text-decoration: underline; }

    .empty {
      display: flex; flex-direction: column; align-items: center; gap: 4px;
      padding: 30px 20px; text-align: center;
      color: var(--ink-4); font-size: 12.5px;
    }
    .et { font-weight: 700; color: var(--ink-3); }
    .es { font-size: 11.5px; }

    .list { max-height: 380px; overflow-y: auto; }

    .item {
      position: relative;
      display: flex; align-items: flex-start; gap: 10px;
      padding: 11px 14px; border-bottom: 1px solid var(--line-soft);
      color: inherit; cursor: pointer;
    }
    .item:last-child { border-bottom: 0; }
    .item:hover, .item:focus-visible { background: var(--surface-3); text-decoration: none; }
    .item.unread { background: var(--accent-soft); }
    .item.unread:hover { background: var(--accent-wash); }

    .body { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
    .ttl {
      font-size: 12.5px; font-weight: 700; color: var(--ink);
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
    }
    .time { font-size: 10.5px; color: var(--ink-4); font-weight: 600; }

    .dot {
      flex: none; width: 7px; height: 7px; margin-top: 5px;
      border-radius: 50%; background: var(--accent);
    }
  `,
})
export class NotificationBell {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  readonly notifications = inject(NotificationService);
  readonly open = signal(false);

  constructor() {
    this.notifications.startPolling(this.destroyRef);
  }

  toggle(): void {
    const next = !this.open();
    this.open.set(next);
    if (next) {
      this.notifications.refresh();
    }
  }

  select(id: number): void {
    this.open.set(false);
    this.notifications.markRead(id);
  }

  fit(score: number): string {
    return `${Math.round(score * 100)}%`;
  }

  timeAgo(iso: string): string {
    const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (seconds < 60) return 'just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}d ago`;
    return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
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
