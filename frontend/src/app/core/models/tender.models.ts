// Mirrors the Java DTOs in com.bracit.tendersense.dto.
// The contract is frozen: change both sides together.

export type SourcePortal = 'EGP_BANGLADESH' | 'WORLD_BANK' | 'UNGM' | 'ISDB' | 'BRAC';

export const SOURCE_LABELS: Record<SourcePortal, string> = {
  EGP_BANGLADESH: 'e-GP Bangladesh',
  WORLD_BANK: 'World Bank',
  UNGM: 'UN Global Marketplace',
  ISDB: 'Islamic Development Bank',
  BRAC: 'BRAC e-Tender',
};

export const SOURCE_OPTIONS: SourcePortal[] = ['EGP_BANGLADESH', 'WORLD_BANK', 'UNGM', 'ISDB', 'BRAC'];
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
  /** This company saved it for later. */
  wishlisted: boolean;
  /** This company marked it as submitted on the portal. */
  submitted: boolean;
  submittedAt: string | null;
  /** The tender's own page on its portal; null when it cannot be built. */
  sourceUrl: string | null;
  /** The local model's shorter title, when the portal's is too long to scan. */
  shortTitle: string | null;
}

/** Headline counts for the tender list, over the same filters as the list. */
export interface TenderListSummary {
  total: number;
  sGrade: number;
  closingSoon: number;
  saved: number;
  submitted: number;
}

/** Shortlist filter: only saved, or only submitted, tenders. */
export type TrackingFilter = 'SAVED' | 'SUBMITTED';

/** A company's saved / submitted state for one tender, as the tracking endpoints return it. */
export interface TrackingState {
  tenderId: number;
  wishlisted: boolean;
  wishlistedAt: string | null;
  submitted: boolean;
  submittedAt: string | null;
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
  sector: string | null;
  /** The sector as the screen shows it, e.g. "IT services". */
  sectorLabel: string | null;
  wishlisted: boolean;
  submitted: boolean;
  submittedAt: string | null;
  /** The tender's own page on its portal; null when there is no verified link. */
  sourceUrl: string | null;
  // Standard form: the same meaning for every portal.
  buyer: string | null;
  partOf: string | null;
  location: string | null;
  category: 'GOODS' | 'WORKS' | 'CONSULTING' | 'OTHER_SERVICES' | null;
  noticeType: 'TENDER' | 'EXPRESSION_OF_INTEREST' | 'PREQUALIFICATION' | 'CONTRACT_AWARD' | 'GENERAL_NOTICE' | null;
  openTo: 'NATIONAL' | 'INTERNATIONAL' | null;
  methodLabel: string | null;
  fundedBy: string | null;
  amendments: number | null;
  // Read by the local model; null until read, or where a field failed its check.
  aiShortTitle: string | null;
  aiSummary: string | null;
  aiDeliverables: string[];
  aiLocation: string | null;
  aiMinTurnoverBdt: number | null;
  aiMinExperienceYears: number | null;
  aiCertifications: string[];
  aiStatus: 'DONE' | 'FAILED' | 'SKIPPED' | null;
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

/** The staging queue between the scrapers and the tender list. */
export interface ProcessingStatus {
  modelEnabled: boolean;
  workerEnabled: boolean;
  model: string;
  waiting: number;
  waitingOpen: number;
  inProgress: number;
  awaitingScore: number;
  done: number;
  failed: number;
  readLastHour: number;
  secondsPerRead: number | null;
  etaMinutes: number | null;
  lastError: string | null;
  lastErrorAt: string | null;
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

// ---- accounts and roles -------------------------------------------------------

export type Role = 'USER' | 'ADMIN';

/** Who is signed in. A platform admin has no company (organisation is null). */
export interface SessionUser {
  accountId: number;
  email: string;
  role: Role;
  organisation: Organisation | null;
}

// ---- pipeline (admin) ---------------------------------------------------------

/** Totals over every collection run, computed in the database. */
export interface RunSummary {
  runsTotal: number;
  failedTotal: number;
  failedLast24h: number;
  lastSuccessAt: string | null;
  tendersScored: number;
}

export interface ScheduleJob {
  key: string;
  label: string;
  description: string;
  cron: string;
  /** What "Back to default" restores. */
  defaultCron: string;
  /** The admin's on/off switch. */
  enabled: boolean;
  /** Sitting out its runs after repeated failures. */
  paused: boolean;
  nextRunAt: string | null;
  /** When the scheduler last fired it (or, before firings were recorded, its last run). */
  lastFiredAt: string | null;
  lastRunAt: string | null;
  lastStatus: RunStatus | null;
  avgDurationMs: number | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

/** A schedule checked before saving: its next runs, or why it would be refused. */
export interface CronPreview {
  cron: string;
  valid: boolean;
  message: string | null;
  nextRuns: string[];
}

export interface Schedule {
  enabled: boolean;
  zone: string;
  jobs: ScheduleJob[];
}

// ---- company dashboard --------------------------------------------------------

export interface TrackingChange {
  tenderId: number;
  title: string | null;
  source: SourcePortal;
  saved: boolean;
  submitted: boolean;
  updatedAt: string;
}

export interface Dashboard {
  numbers: {
    open: number; sGrade: number; aGrade: number;
    closingSoon: number; saved: number; submitted: number;
  };
  bestMatches: TenderSummary[];
  closingSoon: TenderSummary[];
  activity: {
    unread: number;
    newMatches: NotificationItem[];
    tracking: TrackingChange[];
    scoring: ProfileStaleness;
  };
}

// ---- admin panel --------------------------------------------------------------

export interface PortalCorpus {
  portal: SourcePortal;
  total: number; open: number; closed: number;
  newToday: number; newThisWeek: number;
  aiRead: number; aiSkipped: number; aiFailed: number; aiNone: number;
}

export interface CompanyGrades {
  organisationId: number;
  name: string;
  active: boolean;
  s: number; a: number; b: number; c: number;
}

export interface AdminDashboard {
  companies: {
    total: number; active: number; demonstration: number;
    newest: { id: number; name: string; slug: string; active: boolean; createdAt: string | null }[];
  };
  users: {
    total: number; enabled: number; admins: number;
    recentSignIns: { id: number; email: string; company: string | null; role: Role; lastLoginAt: string }[];
  };
  corpus: PortalCorpus[];
  grades: CompanyGrades[];
  runs: RunSummary;
  schedule: Schedule;
  processing: ProcessingStatus;
}

export interface AdminCompany {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  sectors: string[];
  active: boolean;
  demonstration: boolean;
  createdAt: string | null;
  accountId: number | null;
  accountEmail: string | null;
  lastLoginAt: string | null;
  sOpen: number;
  aOpen: number;
}

export interface AdminCompanyDetail {
  company: AdminCompany;
  profile: CapabilityProfile | null;
  scoring: ProfileStaleness;
}

export interface AdminUser {
  id: number;
  email: string;
  role: Role;
  enabled: boolean;
  organisationId: number | null;
  organisationName: string | null;
  organisationActive: boolean;
  createdAt: string | null;
  lastLoginAt: string | null;
  /** The signed-in admin's own row: its role and switch are locked. */
  you: boolean;
}
