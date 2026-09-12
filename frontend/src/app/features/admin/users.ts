import { Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/services/api.service';
import { AdminUser, Role } from '../../core/models/tender.models';
import { apiError, timeAgo } from '../../shared/format';

/**
 * Accounts and access. A company account can be promoted to admin and back; a staff
 * account (no company) can only be an admin. The API refuses anything that could lock
 * the platform out -- demoting or switching off yourself, or the last active admin --
 * and its message is shown as-is.
 */
@Component({
  selector: 'ts-admin-users',
  standalone: true,
  imports: [],
  templateUrl: './users.html',
  styleUrls: ['./admin.css', './users.css'],
})
export class AdminUsers {
  private readonly api = inject(ApiService);

  readonly rows = signal<AdminUser[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly busy = signal<number | null>(null);

  /** The row whose password is being reset, and what has been typed. */
  readonly resetFor = signal<number | null>(null);
  readonly resetPassword = signal('');

  readonly newEmail = signal('');
  readonly newPassword = signal('');
  readonly creating = signal(false);

  readonly timeAgo = timeAgo;
  readonly admins = computed(() => this.rows().filter((u) => u.role === 'ADMIN').length);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listUsers().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiError(err, 'Could not load accounts.'));
        this.loading.set(false);
      },
    });
  }

  setRole(u: AdminUser, role: Role): void {
    if (u.role === role) return;
    this.update(u, { role }, role === 'ADMIN'
      ? `${u.email} is now an admin.`
      : `${u.email} is a company user again.`);
  }

  toggle(u: AdminUser): void {
    if (u.enabled && !confirm(`Switch off ${u.email}? They are signed out at once and cannot sign in `
        + 'until you switch the account back on.')) {
      return;
    }
    this.update(u, { enabled: !u.enabled }, `${u.email} switched ${u.enabled ? 'off' : 'on'}.`);
  }

  startReset(u: AdminUser): void {
    this.resetFor.set(this.resetFor() === u.id ? null : u.id);
    this.resetPassword.set('');
  }

  saveReset(u: AdminUser): void {
    this.busy.set(u.id);
    this.clearMessages();
    this.api.resetPassword(u.id, this.resetPassword()).subscribe({
      next: () => {
        this.busy.set(null);
        this.resetFor.set(null);
        this.resetPassword.set('');
        this.notice.set(`New password set for ${u.email}. Pass it on privately.`);
      },
      error: (err) => {
        this.busy.set(null);
        this.error.set(apiError(err, 'Could not set that password.'));
      },
    });
  }

  create(): void {
    this.creating.set(true);
    this.clearMessages();
    this.api.createAdmin(this.newEmail(), this.newPassword()).subscribe({
      next: (u) => {
        this.creating.set(false);
        this.rows.update((rows) => [u, ...rows]);
        this.newEmail.set('');
        this.newPassword.set('');
        this.notice.set(`${u.email} can now sign in as an admin.`);
      },
      error: (err) => {
        this.creating.set(false);
        this.error.set(apiError(err, 'Could not add that admin.'));
      },
    });
  }

  private update(u: AdminUser, change: { role?: Role; enabled?: boolean }, done: string): void {
    this.busy.set(u.id);
    this.clearMessages();
    this.api.updateUser(u.id, change).subscribe({
      next: (updated) => {
        this.rows.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
        this.busy.set(null);
        this.notice.set(done);
      },
      error: (err) => {
        this.busy.set(null);
        this.error.set(apiError(err, 'Could not change that account.'));
      },
    });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
