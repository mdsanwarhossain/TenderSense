// Mirrors the Java DTOs in com.bracit.tendersense.dto.
// The contract is frozen: change both sides together.

export type SourcePortal = 'EGP_BANGLADESH' | 'WORLD_BANK' | 'UNGM' | 'ISDB';

export const SOURCE_LABELS: Record<SourcePortal, string> = {
  EGP_BANGLADESH: 'e-GP Bangladesh',
  WORLD_BANK: 'World Bank',
  UNGM: 'UN Global Marketplace',
  ISDB: 'Islamic Development Bank',
};

export const SOURCE_OPTIONS: SourcePortal[] = ['EGP_BANGLADESH', 'WORLD_BANK', 'UNGM', 'ISDB'];
export type MatchGrade = 'S' | 'A' | 'B' | 'C';
export type BidAction = 'BID' | 'HOLD' | 'SKIP';
export type EligibilityStatus = 'ELIGIBLE' | 'INELIGIBLE' | 'NEEDS_VERIFICATION';
export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TenderSummary {
  id: number;
  externalId: string;
  source: SourcePortal;
  title: string | null;
  procuringEntity: string | null;
  procurementNature: string | null;
  procurementMethod: string | null;
  closingAt: string | null;
  daysToDeadline: number | null;
  urgent: boolean;
  grade: MatchGrade | null;
  score: number | null;
  eligibility: EligibilityStatus | null;
  blockingGapCount: number;
  recommendation: BidAction | null;
  whyMatched: string | null;
}

export interface TenderDetail {
  id: number;
  externalId: string;
  source: SourcePortal;
  referenceNo: string | null;
  title: string | null;
  description: string | null;
  ministry: string | null;
  division: string | null;
  organization: string | null;
  procuringEntity: string | null;
  district: string | null;
  country: string | null;
  procurementNature: string | null;
  procurementType: string | null;
  procurementMethod: string | null;
  budgetType: string | null;
  sourceOfFunds: string | null;
  documentPriceBdt: number | null;
  publishedAt: string | null;
  closingAt: string | null;
  daysToDeadline: number | null;
  status: string | null;
  eligibilityText: string | null;
  /** Provenance: the raw snapshot this row was parsed from. */
  rawSnapshotPath: string | null;
  contentHash: string | null;
  revisionCount: number;
}

export interface EvidencePair {
  profileText: string;
  tenderText: string;
  similarity: number;
}

export interface MatchEvidence {
  tenderId: number;
  grade: MatchGrade | null;
  score: number;
  modelVersion: string | null;
  summary: string | null;
  recommendation: BidAction | null;
  /** Set when the score was pulled down for resembling excluded work. */
  demotedFor: string | null;
  demotionPenalty: number | null;
  evidence: EvidencePair[];
}

export interface EligibilityGap {
  ruleCode: string;
  requirement: string | null;
  actual: string | null;
  blocking: boolean;
  message: string | null;
}

export interface EligibilityReport {
  tenderId: number;
  status: EligibilityStatus;
  rulesApplied: string | null;
  gaps: EligibilityGap[];
}

export interface MatcherScore {
  matcher: string;
  hits: number;
  total: number;
  precisionAtK: number;
}

export interface BenchmarkResult {
  heldOutCount: number;
  k: number;
  semantic: MatcherScore;
  keyword: MatcherScore;
  comparisons: {
    tenderId: number;
    title: string;
    labelledRelevant: boolean;
    semanticRank: number | null;
    keywordRank: number | null;
  }[];
  /** Shown verbatim in the UI: the metric's honest limitations. */
  caveat: string | null;
}

export interface PipelineRun {
  id: number;
  jobName: string;
  status: RunStatus;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  tendersDiscovered: number | null;
  tendersDetailed: number | null;
  tendersScored: number | null;
  errorMessage: string | null;
}

export interface CapabilityProfile {
  id: number;
  orgName: string;
  summary: string | null;
  annualTurnoverBdt: number | null;
  services: string[];
  exclusions: string[];
  sectors: string[];
  geographies: string[];
  pastProjects: {
    id: number; title: string; client: string | null; description: string | null;
    sector: string | null; valueBdt: number | null; year: number | null;
  }[];
  certifications: { id: number; code: string; name: string | null; validUntil: string | null }[];
}

export interface BidDecisionRequest {
  action: BidAction;
  note?: string;
  decidedBy?: string;
}

/** A subscribing company. `sectors` is the hard gate: it sees nothing outside them. */
export interface Organisation {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  sectors: string[];
  demonstration: boolean;
}

/** Whether the stored scores still reflect the stored profile. */
export interface ProfileStaleness {
  stale: boolean;
  profileUpdatedAt: string | null;
  lastScoredAt: string | null;
  scoredCount: number;
  scorable: number;
}

export interface SectorOption {
  value: string;
  label: string;
}

/** One row of the notification bell: a tender that newly matched this company's profile. */
export interface NotificationItem {
  id: number;
  tenderId: number;
  tenderTitle: string | null;
  procuringEntity: string | null;
  grade: MatchGrade | null;
  score: number;
  closingAt: string | null;
  read: boolean;
  createdAt: string;
}
