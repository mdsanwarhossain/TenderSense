import { Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ProcessingStatus } from '../core/models/tender.models';

/**
 * The queue between the scrapers and the tender list: the local model reads open tenders
 * here, a few at a time, before they reach the list. Shown on the admin dashboard and
 * the Pipeline page.
 */
@Component({
  selector: 'ts-ai-queue-card',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    @let q = queue();
    <section class="card">
      <div class="card-head">
        <div>
          <h2>AI processing queue</h2>
          <span class="sub">New tenders wait here while the local model reads them, a few at a time.</span>
        </div>
        <span class="pill" [class.accent]="q.modelEnabled" [class.ghost]="!q.modelEnabled">
          {{ q.modelEnabled ? 'Model on · ' + q.model : 'Model off' }}
        </span>
      </div>
      <div class="queue-grid">
        <div class="qcell">
          <span class="label">Waiting</span>
          <span class="qnum">{{ q.waitingOpen | number }}</span>
          <span class="hint">open tenders{{ q.waiting > q.waitingOpen ? ' · ' + (q.waiting - q.waitingOpen) + ' closed' : '' }}</span>
        </div>
        <div class="qcell">
          <span class="label">Being read</span>
          <span class="qnum">{{ q.inProgress }}</span>
          <span class="hint">{{ q.awaitingScore }} waiting to be scored</span>
        </div>
        <div class="qcell">
          <span class="label">Read in the last hour</span>
          <span class="qnum">{{ q.readLastHour }}</span>
          <span class="hint">{{ q.done | number }} processed in total</span>
        </div>
        <div class="qcell">
          <span class="label">Time per tender</span>
          <span class="qnum">{{ q.secondsPerRead !== null ? q.secondsPerRead + 's' : '—' }}</span>
          <span class="hint">measured on this machine</span>
        </div>
        <div class="qcell">
          <span class="label">Time left</span>
          <span class="qnum">{{ eta(q.etaMinutes) }}</span>
          <span class="hint">for the open tenders waiting</span>
        </div>
      </div>
      @if (q.failed > 0) {
        <p class="card-note">{{ q.failed }} could not be processed and need a developer.</p>
      }
      @if (q.lastError) {
        <p class="card-note qerr">Last problem{{ q.lastErrorAt ? ' at ' + (q.lastErrorAt | date: 'dd MMM HH:mm') : '' }}: {{ q.lastError }}</p>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .card-note { margin: 0; }
    .queue-grid {
      display: grid;
      grid-template-columns: repeat(5, minmax(0, 1fr));
      border-top: 1px solid var(--line-soft);
    }
    .qcell {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 16px 22px;
      border-right: 1px solid var(--line-soft);
    }
    .qcell:last-child { border-right: 0; }
    .qnum { font-size: 24px; font-weight: 800; letter-spacing: -0.5px; }
    .qerr { color: var(--bad); }
    @media (max-width: 900px) {
      .queue-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .qcell { border-bottom: 1px solid var(--line-soft); }
    }
  `,
})
export class AiQueueCard {
  readonly queue = input.required<ProcessingStatus>();

  eta(minutes: number | null): string {
    if (minutes === null) return '—';
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    return h < 48 ? `${h} h ${minutes % 60} min` : `${Math.round(h / 24 * 10) / 10} days`;
  }
}
