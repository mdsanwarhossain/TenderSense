import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BenchmarkResult, BidDecisionRequest, CapabilityProfile, EligibilityReport,
  MatchEvidence, MatchGrade, PageResponse, PipelineRun, SourcePortal,
  TenderDetail, TenderSummary,
} from '../models/tender.models';

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
    includeClosed?: boolean;
  } = {}): Observable<PageResponse<TenderSummary>> {
    let params = new HttpParams()
      .set('page', String(opts.page ?? 0))
      .set('size', String(opts.size ?? 20))
      .set('includeClosed', String(opts.includeClosed ?? false));
    if (opts.grade) params = params.set('grade', opts.grade);
    if (opts.source) params = params.set('source', opts.source);
    return this.http.get<PageResponse<TenderSummary>>(`${this.base}/tenders`, { params });
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

  /** `full` runs the reconcile crawl; the default incremental sweep skips known ids. */
  runPipeline(full = false): Observable<PipelineRun[]> {
    const params = new HttpParams().set('full', String(full));
    return this.http.post<PipelineRun[]>(`${this.base}/pipeline/run`, null, { params });
  }

  getRuns(): Observable<PipelineRun[]> {
    return this.http.get<PipelineRun[]>(`${this.base}/pipeline/runs`);
  }
}
