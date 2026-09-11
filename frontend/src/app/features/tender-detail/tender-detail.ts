import { Component, computed, inject, input, signal } from '@angular/core';
import { formatDate } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { GradeBadge } from '../../shared/grade-badge';
import { EligibilityChip } from '../../shared/eligibility-chip';
import { TenderActions } from '../../shared/tender-actions';
import {
  BidAction,
  EligibilityReport,
  MatchEvidence,
  MatchGrade,
  SOURCE_LABELS,
  SourcePortal,
  TenderDetail as TenderDetailModel,
} from '../../core/models/tender.models';

/** One plain sentence per grade, in place of the raw similarity numbers. */
const HEADLINES: Record<MatchGrade, string> = {
  S: 'An excellent match for your company.',
  A: 'A strong match for your company.',
  B: 'A partial match for your company.',
  C: 'A weak match for your company.',
};

const ADVICE: Record<BidAction, { label: string; hint: string }> = {
  BID: { label: 'Bid', hint: 'A strong match, and you meet the requirements we check.' },
  HOLD: { label: 'Hold', hint: 'Worth a look -- check the requirements before bidding.' },
  SKIP: { label: 'Skip', hint: 'A weak match, or a requirement you do not meet.' },
};

const CATEGORY_LABELS: Record<string, string> = {
  GOODS: 'Goods', WORKS: 'Works', CONSULTING: 'Consulting services', OTHER_SERVICES: 'Other services',
};
const NOTICE_LABELS: Record<string, string> = {
  TENDER: 'Tender', EXPRESSION_OF_INTEREST: 'Expression of interest', PREQUALIFICATION: 'Prequalification',
  CONTRACT_AWARD: 'Contract award (already awarded)', GENERAL_NOTICE: 'General notice',
};

/** Evidence below this similarity is too weak to present as a reason. */
const EVIDENCE_FLOOR = 0.2;

/**
 * One tender in full, written for the tender team rather than for engineers: plain
 * words, percentages, and the matched services as chips instead of raw passages.
 */
@Component({
  selector: 'ts-tender-detail',
  standalone: true,
  imports: [RouterLink, GradeBadge, EligibilityChip, TenderActions],
  templateUrl: './tender-detail.html',
  styleUrl: './tender-detail.css',
})
export class TenderDetail {
  private readonly api = inject(ApiService);

  /** Bound from the route via withComponentInputBinding(). */
  readonly id = input.required<string>();

  readonly tender = signal<TenderDetailModel | null>(null);
  readonly evidence = signal<MatchEvidence | null>(null);
  readonly eligibility = signal<EligibilityReport | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** A save / submit that could not be recorded. Shown under the header, not instead of the page. */
  readonly actionError = signal<string | null>(null);

  readonly blockingGaps = computed(
    () => this.eligibility()?.gaps.filter((g) => g.blocking) ?? [],
  );
  readonly checkGaps = computed(
    () => this.eligibility()?.gaps.filter((g) => !g.blocking) ?? [],
  );

  /**
   * The information table, in labels that fit every portal. A field the portal does not
   * give is left out rather than shown as a dash.
   */
  readonly facts = computed(() => {
    const t = this.tender();
    if (!t) return [];
    const when = (v: string | null) => (v ? formatDate(v, 'd MMM y, h:mm a', 'en-US') : null);
    // A country alone says little; the model's reading of the notice may name the district.
    const place = t.location && t.location !== t.country ? t.location : (t.aiLocation ?? t.location);
    const rows: { label: string; value: string | null; warn?: boolean }[] = [
      { label: 'Issued by', value: t.buyer },
      { label: 'Part of', value: t.partOf },
      { label: 'Location', value: place },
      { label: 'Category', value: t.category ? CATEGORY_LABELS[t.category] : null },
      { label: 'Notice type', value: t.noticeType ? NOTICE_LABELS[t.noticeType] : null },
      { label: "How it's awarded", value: t.methodLabel },
      { label: 'Open to', value: t.openTo === 'NATIONAL' ? 'Bidders in Bangladesh'
          : t.openTo === 'INTERNATIONAL' ? 'Bidders from any country' : null },
      { label: 'Funded by', value: t.fundedBy },
      // e-GP falls back to the package description for the reference; that is the title again.
      { label: 'Reference no.', value: t.referenceNo && t.referenceNo !== t.title ? t.referenceNo : null },
      { label: 'Document price', value: t.documentPriceBdt ? 'BDT ' + t.documentPriceBdt.toLocaleString('en-US') : null },
      { label: 'Published', value: when(t.publishedAt) },
      { label: 'Closing', value: when(t.closingAt) ?? 'Not stated', warn: this.urgent() },
      { label: 'Changes', value: t.amendments ? `Amended ${t.amendments} time${t.amendments === 1 ? '' : 's'}` : null },
    ];
    return rows.filter((r) => r.value);
  });

  /** What the model read out of the eligibility text, each already checked against it. */
  readonly aiRequirements = computed(() => {
    const t = this.tender();
    if (!t) return [];
    const out: string[] = [];
    if (t.aiMinTurnoverBdt) out.push(`Minimum annual turnover: BDT ${t.aiMinTurnoverBdt.toLocaleString('en-US')}`);
    if (t.aiMinExperienceYears) out.push(`At least ${t.aiMinExperienceYears} years of experience`);
    if (t.aiCertifications?.length) out.push(`Licences and certificates: ${t.aiCertifications.join(', ')}`);
    return out;
  });

  /** Same rounding as the list's Match column, so the two screens agree. */
  readonly percent = computed(() => {
    const s = this.evidence()?.score;
    return s == null ? '—' : `${Math.round(Math.min(1, Math.max(0, s)) * 100)}%`;
  });

  readonly closesIn = computed(() => {
    const d = this.tender()?.daysToDeadline;
    if (d == null) return 'Not stated';
    if (d < 0) return 'Closed';
    if (d === 0) return 'Today';
    return d === 1 ? '1 day' : `${d} days`;
  });

  /** Same window as the amber flag on the list. */
  readonly urgent = computed(() => {
    const d = this.tender()?.daysToDeadline;
    return d != null && d >= 0 && d <= 7;
  });

  readonly headline = computed(() => {
    const g = this.evidence()?.grade;
    return g ? HEADLINES[g] : null;
  });

  readonly advice = computed(() => {
    const r = this.evidence()?.recommendation;
    return r ? { action: r, ...ADVICE[r] } : null;
  });

  /**
   * The company's services that this tender lines up with, strongest first, as short
   * chip names. The tender-side passages are not shown: they are fragments of the
   * notice stitched together for matching, and read as noise.
   */
  readonly services = computed(() => {
    const seen = new Set<string>();
    const out: { name: string; full: string }[] = [];
    for (const e of this.evidence()?.evidence ?? []) {
      if (e.similarity < EVIDENCE_FLOOR) continue;
      const name = this.shortName(e.profileText);
      const key = name.toLowerCase();
      if (!name || seen.has(key)) continue;
      seen.add(key);
      out.push({ name, full: e.profileText });
    }
    return out;
  });

  constructor() {
    queueMicrotask(() => this.load());
  }

  private load(): void {
    const id = Number(this.id());
    if (!Number.isFinite(id)) {
      this.error.set('That is not a valid tender id.');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      tender: this.api.getTender(id),
      evidence: this.api.getEvidence(id),
      eligibility: this.api.getEligibility(id),
    }).subscribe({
      next: ({ tender, evidence, eligibility }) => {
        this.tender.set(tender);
        this.evidence.set(evidence);
        this.eligibility.set(eligibility);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(this.describe(err));
        this.loading.set(false);
      },
    });
  }

  /**
   * A profile statement shortened to a chip: its first sentence, cut at a word
   * boundary. Past-project statements run to several sentences; the first names the work.
   */
  shortName(text: string): string {
    const first = text.split(/(?<=\.)\s+/)[0].replace(/\.$/, '').trim();
    if (first.length <= 80) return first;
    const cut = first.slice(0, 80);
    return cut.slice(0, cut.lastIndexOf(' ')) + '…';
  }

  sourceLabel(source: string | undefined): string {
    return source ? (SOURCE_LABELS[source as SourcePortal] ?? source) : 'Unknown source';
  }

  private describe(err: unknown): string {
    const e = err as { error?: { detail?: string }; status?: number; message?: string };
    if (e?.status === 404) return 'That tender could not be found.';
    if (e?.error?.detail) return e.error.detail;
    if (e?.status === 0) return 'Cannot reach the server. Check that TenderSense is running.';
    return e?.message ?? 'Something went wrong loading this tender.';
  }
}
