export type UserRole = 'ADMIN' | 'REGISTRAR' | 'JUDGE';

export interface AuthUser {
  authenticated: boolean;
  username: string;
  roles: string[];
  judgeId?: string | null;
}

export type CaseType = 'BAIL' | 'POCSO' | 'MATRIMONIAL' | 'CIVIL' | 'CRIMINAL_OTHER';
export type CaseStatus = 'FILED' | 'SCHEDULED' | 'HEARD' | 'DISPOSED' | 'ADJOURNED' | 'PENDING';
export type HearingStatus = 'SCHEDULED' | 'HEARD' | 'CANCELLED' | 'RESCHEDULED' | 'ADJOURNED';

export interface Case {
  id: string;
  caseNumber: string;
  caseType: CaseType;
  filingDate: string;
  currentStatus: CaseStatus;
  priorAdjournments: number;
  statutoryDeadline?: string | null;
  priorityScore?: number | null;
  assignedJudge?: { id: string; name: string } | null;
  assignedCourtroom?: { id: string; name: string } | null;
  nextHearingDate?: string | null;
  litigantContactEmail?: string | null;
}

export interface ScoreFactorBreakdown {
  factorName: string;
  rawMetricValue: string;
  normalizedScore: number;
  weight: number;
  contribution: number;
  explanation: string;
}

export interface PriorityScoreResult {
  caseId: string;
  caseNumber: string;
  totalScore: number;
  factors: ScoreFactorBreakdown[];
  summary: string;
  computedAt: string;
  persistedScoreId: string;
}

export interface Hearing {
  id: string;
  caseId: string;
  caseNumber: string;
  judgeId: string;
  judgeName: string;
  courtroomId: string;
  courtroomName: string;
  scheduledTime: string;
  durationMinutes: number;
  status: HearingStatus;
}

export interface Judge {
  id: string;
  name: string;
  specialization?: string | null;
}

export interface Courtroom {
  id: string;
  name: string;
  building?: string | null;
}

export interface Proposal {
  proposalId: string;
  caseId: string;
  caseNumber: string;
  judgeId: string;
  judgeName: string;
  courtroomId: string;
  courtroomName: string;
  slotStartTime: string;
  durationMinutes: number;
  scoreAtScheduling: number;
  status: 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED';
  rejectionReason?: string | null;
  explanation?: string | null;
  softCostPenalty?: number | null;
}

export interface SchedulingRun {
  runId: string;
  status: string;
  triggeredAt: string;
  completedAt?: string | null;
  totalCasesInput: number;
  totalAssigned: number;
  totalUnschedulable: number;
  horizonDays: number;
  proposals: Proposal[];
}

export interface AgingReportItem {
  caseId: string;
  caseNumber: string;
  caseType: CaseType;
  status: CaseStatus;
  filingDate: string;
  daysPending: number;
  adjournments: number;
  statutoryDeadline?: string | null;
  daysToDeadline?: number | null;
  priorityScore: number;
  assignedJudgeName?: string | null;
  assignedCourtroomName?: string | null;
}

export interface AuditLogEntry {
  id: string;
  actorId?: string | null;
  actorUsername: string;
  actorRole: string;
  action: string;
  entityType: string;
  entityId: string;
  timestamp: string;
  reasonCode: string;
  beforeState?: Record<string, any> | null;
  afterState?: Record<string, any> | null;
  details?: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ScheduleDiffSummary {
  runId: string;
  previousRunId?: string | null;
  totalProposals: number;
  previousProposals: number;
  newProposals: number;
  modifiedProposals: number;
  unchangedProposals: number;
  reprioritizedCasesCount: number;
  assignmentChanges: Array<{
    caseId: string;
    caseNumber: string;
    changeType: 'NEW' | 'MODIFIED' | 'UNCHANGED' | 'REMOVED' | 'COMMITTED';
    details: string;
  }>;
}

export interface BatchJobStatusResponse {
  jobExecutionId: number;
  status: string;
  exitStatus: string;
  startTime?: string | null;
  endTime?: string | null;
  schedulingRunId?: string | null;
}
