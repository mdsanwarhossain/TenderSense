import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BenchmarkResult, BidDecisionRequest, CapabilityProfile, EligibilityReport,
  MatchEvidence, MatchGrade, NotificationItem, PageResponse, PipelineRun, ProfileStaleness,
  SectorOption, SourcePortal, TenderDetail, TenderSummary, TenderListSummary, TrackingFilter, TrackingState, ProcessingStatus } from '../models/tender.models';

/**
 * Single place the frontend talks to the backend.
 *
 * Requests go to a relative /api path: `ng serve` proxies it to :8080 in dev, and
 * the packaged build is served from the same origin, so no base URL is ever
 * configured and CORS never applies in the demo path.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  listTenders(opts: {
    page?: number; size?: number; grade?: MatchGrade; source?: SourcePortal;
    includeClosed?: boolean; tracked?: TrackingFilter; closingSoon?: boolean;
  } = {}): Observable<PageResponse<TenderSummary>> {
    let params = new HttpParams()
      .set('page', String(opts.page ?? 0))
      .set('size', String(opts.size ?? 20))
      .set('includeClosed', String(opts.includeClosed ?? false));
    if (opts.grade) params = params.set('grade', opts.grade);
    if (opts.source) params = params.set('source', opts.source);
    if (opts.tracked) params = params.set('tracked', opts.tracked);
    if (opts.closingSoon) params = params.set('closingSoon', 'true');
    return this.http.get<PageResponse<TenderSummary>>(`${this.base}/tenders`, { params });
  }

  /**
   * Counts for the summary cards, taken in the database rather than from the page of rows
   * on screen. Scoped by source and Include closed only -- the cards themselves are the
   * other filters, and clicking one must not change the others' counts.
   */
  getListSummary(opts: {
    source?: SourcePortal; includeClosed?: boolean;
  } = {}): Observable<TenderListSummary> {
    let params = new HttpParams().set('includeClosed', String(opts.includeClosed ?? false));
    if (opts.source) params = params.set('source', opts.source);
    return this.http.get<TenderListSummary>(`${this.base}/tenders/summary`, { params });
  }

  /** Save for later (PUT) or remove from saved (DELETE). Idempotent, never a toggle. */
  setWishlisted(tenderId: number, on: boolean): Observable<TrackingState> {
    const url = `${this.base}/tenders/${tenderId}/wishlist`;
    return on ? this.http.put<TrackingState>(url, null) : this.http.delete<TrackingState>(url);
  }

  /** Mark (PUT) or unmark (DELETE) a tender as submitted on its portal. */
  setSubmitted(tenderId: number, on: boolean): Observable<TrackingState> {
    const url = `${this.base}/tenders/${tenderId}/submission`;
    return on ? this.http.put<TrackingState>(url, null) : this.http.delete<TrackingState>(url);
  }

  getTender(id: number): Observable<TenderDetail> {
    return this.http.get<TenderDetail>(`${this.base}/tenders/${id}`);
  }

  getEvidence(id: number): Observable<MatchEvidence> {
    return this.http.get<MatchEvidence>(`${this.base}/tenders/${id}/evidence`);
  }

  getEligibility(id: number): Observable<EligibilityReport> {
    return this.http.get<EligibilityReport>(`${this.base}/tenders/${id}/eligibility`);
  }

  recordDecision(id: number, body: BidDecisionRequest): Observable<BidDecisionRequest> {
    return this.http.post<BidDecisionRequest>(`${this.base}/tenders/${id}/decision`, body);
  }

  getBenchmark(): Observable<BenchmarkResult> {
    return this.http.get<BenchmarkResult>(`${this.base}/benchmark`);
  }

  getProfile(): Observable<CapabilityProfile | null> {
    return this.http.get<CapabilityProfile | null>(`${this.base}/profile`);
  }

  updateProfile(body: unknown): Observable<CapabilityProfile> {
    return this.http.put<CapabilityProfile>(`${this.base}/profile`, body);
  }

  /** Whether the stored scores still reflect the stored profile. */
  getStaleness(): Observable<ProfileStaleness> {
    return this.http.get<ProfileStaleness>(`${this.base}/profile/staleness`);
  }

  listSectors(): Observable<SectorOption[]> {
    return this.http.get<SectorOption[]>(`${this.base}/sectors`);
  }

  /**
   * Re-scores this company's tenders against its current profile. Distinct from
   * {@link runPipeline}, which collects new tenders from the portals.
   */
  rescore(): Observable<PipelineRun> {
    return this.http.post<PipelineRun>(`${this.base}/pipeline/rescore`, null);
  }

  /** `full` runs the reconcile crawl; the default incremental sweep skips known ids. */
  runPipeline(full = false): Observable<PipelineRun[]> {
    const params = new HttpParams().set('full', String(full));
    return this.http.post<PipelineRun[]>(`${this.base}/pipeline/run`, null, { params });
  }

  getRuns(): Observable<PipelineRun[]> {
    return this.http.get<PipelineRun[]>(`${this.base}/pipeline/runs`);
  }

  getProcessingStatus(): Observable<ProcessingStatus> {
    return this.http.get<ProcessingStatus>(`${this.base}/pipeline/processing`);
  }

  listNotifications(opts: { unreadOnly?: boolean; page?: number; size?: number } = {}):
      Observable<PageResponse<NotificationItem>> {
    const params = new HttpParams()
      .set('page', String(opts.page ?? 0))
      .set('size', String(opts.size ?? 8))
      .set('unreadOnly', String(opts.unreadOnly ?? false));
    return this.http.get<PageResponse<NotificationItem>>(`${this.base}/notifications`, { params });
  }

  /** Polled by the bell: the red count, without pulling the notifications themselves. */
  getUnreadNotificationCount(): Observable<number> {
    return this.http.get<number>(`${this.base}/notifications/unread-count`);
  }

  markNotificationRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/notifications/${id}/read`, null);
  }

  markAllNotificationsRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/notifications/read-all`, null);
  }
}
