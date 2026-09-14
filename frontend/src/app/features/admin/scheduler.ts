import { Component, DestroyRef, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { CronPreview, Schedule, ScheduleJob } from '../../core/models/tender.models';
import { apiError, duration, timeAgo, timeUntil } from '../../shared/format';
import {
  DAYS, EVERY_OPTIONS, HOURS, MONTH_DAYS, SCHEDULE_TYPES, ScheduleForm, defaultForm, describeCron,
  formFromCron, formProblem, formToCron, minuteOptions, pad,
} from './schedule-form';

/** How long the modal takes to grow or shrink to fit what it is showing. */
const RESIZE_MS = 240;

/**
 * Switch the scheduled jobs on and off, and reschedule them. Saved to the database and
 * applied at once -- no restart. The server refuses a schedule that would run too often,
 * and its message is shown as-is.
 */
@Component({
  selector: 'ts-admin-scheduler',
  standalone: true,
  imports: [DatePipe, TitleCasePipe],
  templateUrl: './scheduler.html',
  styleUrls: ['./admin.css', './scheduler.css'],
})
export class AdminScheduler {
  private readonly api = inject(ApiService);

  readonly data = signal<Schedule | null>(null);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly busy = signal<string | null>(null);

  // ---- the Reschedule modal ----
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly modalBody = viewChild<ElementRef<HTMLElement>>('modalBody');
  private readonly modalBodyInner = viewChild<ElementRef<HTMLElement>>('modalBodyInner');

  /** The job being rescheduled; null while the modal is shut. */
  readonly job = signal<ScheduleJob | null>(null);
  readonly form = signal<ScheduleForm>(defaultForm());
  /** The job's current schedule is a shape the form cannot show. */
  readonly unreadable = signal(false);
  readonly problem = computed(() => formProblem(this.form()));
  /** The standard form of what is filled in; null until it is complete. */
  readonly cron = computed(() => (this.problem() ? null : formToCron(this.form())));
  /** The server's check of {@link cron}: whether it is allowed, and why not if it is refused. */
  readonly preview = signal<CronPreview | null>(null);
  readonly canSave = computed(() => {
    const cron = this.cron();
    const preview = this.preview();
    return !!cron && !!preview?.valid && preview.cron === cron && cron !== this.job()?.cron;
  });

  readonly types = SCHEDULE_TYPES;
  readonly everyOptions = EVERY_OPTIONS;
  readonly hours = HOURS;
  readonly days = DAYS;
  readonly monthDays = MONTH_DAYS;
  readonly minutes = computed(() => minuteOptions(this.form().every));

  readonly timeAgo = timeAgo;
  readonly timeUntil = timeUntil;
  readonly duration = duration;
  readonly pad = pad;
  readonly describe = describeCron;

  private stopWatchingHeight?: () => void;

  constructor() {
    // Ask the server about each schedule as it is filled in, once the form settles.
    effect((onCleanup) => {
      const cron = this.cron();
      const job = this.job();
      if (!cron || !job) {
        this.preview.set(null);
        return;
      }
      const timer = setTimeout(() => this.api.previewCron(job.key, cron).subscribe({
        next: (p) => {
          if (this.cron() === cron) this.preview.set(p);
        },
        error: () => this.preview.set(null),
      }), 250);
      onCleanup(() => clearTimeout(timer));
    });
    inject(DestroyRef).onDestroy(() => this.stopWatchingHeight?.());
    this.load();
  }

  load(): void {
    this.api.getAdminSchedule().subscribe({
      next: (s) => this.data.set(s),
      error: (err) => this.error.set(apiError(err, 'Could not load the schedule.')),
    });
  }

  toggle(job: ScheduleJob): void {
    this.busy.set(job.key);
    this.clearMessages();
    this.api.updateScheduleJob(job.key, { enabled: !job.enabled }).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.notice.set(updated.enabled
          ? `${updated.label} is on.`
          : `${updated.label} is off. It will not run until you switch it back on.`);
      },
      error: (err) => this.fail(err),
    });
  }

  // ---- modal ----

  open(job: ScheduleJob): void {
    const form = formFromCron(job.cron);
    this.unreadable.set(!form);
    this.form.set(form ?? defaultForm());
    this.preview.set(null);
    this.job.set(job);
    this.dialog().nativeElement.showModal();
    requestAnimationFrame(() => this.watchHeight());
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  /** The dialog's own close event: Esc, Cancel, the backdrop, or after saving. */
  closed(): void {
    this.stopWatchingHeight?.();
    this.stopWatchingHeight = undefined;
    this.job.set(null);
  }

  /** A click on the dimmed backdrop lands on the dialog element itself. */
  backdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) this.close();
  }

  patch(change: Partial<ScheduleForm>): void {
    this.form.update((f) => {
      const next = { ...f, ...change };
      // A 30-minute repeat runs at :MM and :MM+30, so its minute stays in the first half hour.
      if (next.every === 30 && next.minute >= 30) next.minute %= 30;
      return next;
    });
  }

  toggleDay(day: string): void {
    const days = this.form().days;
    this.patch({ days: days.includes(day) ? days.filter((d) => d !== day) : [...days, day] });
  }

  num(event: Event): number {
    return Number((event.target as HTMLSelectElement).value);
  }

  text(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  save(job: ScheduleJob): void {
    const cron = this.cron();
    if (!cron) return;
    this.busy.set(job.key);
    this.clearMessages();
    this.api.updateScheduleJob(job.key, { cron }).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.close();
        this.notice.set(`${updated.label} rescheduled: ${describeCron(updated.cron).toLowerCase()}.`);
      },
      error: (err) => this.fail(err),
    });
  }

  reset(job: ScheduleJob): void {
    this.busy.set(job.key);
    this.clearMessages();
    this.api.resetScheduleJob(job.key).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.close();
        this.notice.set(`${updated.label} is back on its default schedule.`);
      },
      error: (err) => this.fail(err),
    });
  }

  /**
   * Grows and shrinks the modal to fit its form. Each type needs different inputs, and a
   * refusal adds a line -- without this the dialog would jump to its new size, which from
   * a fixed, centred box reads as a flicker.
   */
  private watchHeight(): void {
    const box = this.modalBody()?.nativeElement;
    const content = this.modalBodyInner()?.nativeElement;
    if (!box || !content || matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    let height = content.offsetHeight;
    const observer = new ResizeObserver(() => {
      const next = content.offsetHeight;
      if (next === height) {
        return;
      }
      const from = height;
      height = next;
      // Clipped only while it moves, so a long form still scrolls the rest of the time.
      box.style.overflow = 'hidden';
      const animation = box.animate(
        [{ height: `${from}px` }, { height: `${next}px` }],
        { duration: RESIZE_MS, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)' });
      animation.onfinish = () => {
        box.style.overflow = '';
      };
    });
    observer.observe(content);
    this.stopWatchingHeight = () => observer.disconnect();
  }

  private replace(updated: ScheduleJob): void {
    this.busy.set(null);
    this.data.update((s) => s && { ...s, jobs: s.jobs.map((j) => (j.key === updated.key ? updated : j)) });
  }

  private fail(err: unknown): void {
    this.busy.set(null);
    this.error.set(apiError(err, 'Could not change that job.'));
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
