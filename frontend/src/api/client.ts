import {
  AuthUser,
  Case,
  Hearing,
  Judge,
  Courtroom,
  Proposal,
  SchedulingRun,
  AgingReportItem,
  AuditLogEntry,
  PageResponse,
  PriorityScoreResult,
  ScheduleDiffSummary,
  BatchJobStatusResponse,
} from '../types/api';

class ApiClient {
  private authHeader: string | null = null;
  private onUnauthorizedCallback: (() => void) | null = null;

  setCredentials(username: string, password: string): void {
    const token = btoa(`${username}:${password}`);
    this.authHeader = `Basic ${token}`;
  }

  clearCredentials(): void {
    this.authHeader = null;
  }

  hasCredentials(): boolean {
    return this.authHeader !== null;
  }

  onUnauthorized(callback: () => void): void {
    this.onUnauthorizedCallback = callback;
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...(options.headers as Record<string, string>),
    };

    if (this.authHeader) {
      headers['Authorization'] = this.authHeader;
    }

    const response = await fetch(endpoint, {
      ...options,
      headers,
    });

    if (response.status === 401) {
      this.clearCredentials();
      if (this.onUnauthorizedCallback) {
        this.onUnauthorizedCallback();
      }
      throw new Error('Authentication required');
    }

    if (!response.ok) {
      let errorMessage = `HTTP Error ${response.status}`;
      try {
        const errorJson = await response.json();
        errorMessage = errorJson.message || errorJson.error || errorMessage;
      } catch {
        const text = await response.text();
        if (text) errorMessage = text;
      }
      const error: any = new Error(errorMessage);
      error.status = response.status;
      throw error;
    }

    if (response.status === 204) {
      return {} as T;
    }

    return response.json();
  }

  // --- Auth ---
  async getMe(): Promise<AuthUser> {
    return this.request<AuthUser>('/api/v1/auth/me');
  }

  // --- Cases ---
  async listCases(page = 0, size = 15, sortBy = 'filingDate', direction = 'DESC', type?: string, status?: string, search?: string): Promise<PageResponse<Case>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sort: `${sortBy},${direction}`,
    });
    if (type) params.append('caseType', type);
    if (status) params.append('status', status);
    if (search && search.trim()) params.append('search', search.trim());
    return this.request<PageResponse<Case>>(`/api/v1/cases?${params.toString()}`);
  }

  async getCase(id: string): Promise<Case> {
    return this.request<Case>(`/api/v1/cases/${id}`);
  }

  async getAgingReport(caseType?: string, limit = 50): Promise<AgingReportItem[]> {
    const params = new URLSearchParams({ limit: limit.toString() });
    if (caseType) params.append('caseType', caseType);
    return this.request<AgingReportItem[]>(`/api/v1/cases/aging-report?${params.toString()}`);
  }

  // --- Priority Scoring ---
  async getCaseScore(caseId: string): Promise<PriorityScoreResult> {
    return this.request<PriorityScoreResult>(`/api/v1/priority/cases/${caseId}/score/latest`);
  }

  async getCaseScoreHistory(caseId: string): Promise<PriorityScoreResult[]> {
    return this.request<PriorityScoreResult[]>(`/api/v1/priority/cases/${caseId}/history`);
  }

  // --- Hearings ---
  async listHearings(from?: string, to?: string, judgeId?: string, courtroomId?: string): Promise<Hearing[]> {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    if (judgeId) params.append('judgeId', judgeId);
    if (courtroomId) params.append('courtroomId', courtroomId);
    return this.request<Hearing[]>(`/api/v1/hearings?${params.toString()}`);
  }

  async getHearingForCase(caseId: string): Promise<Hearing | null> {
    try {
      const res = await this.request<Hearing | null>(`/api/v1/hearings/cases/${caseId}`);
      if (!res || !res.id || Object.keys(res).length === 0) {
        return null;
      }
      return res;
    } catch (e: any) {
      if (e.status === 404) return null;
      throw e;
    }
  }

  async reassignHearing(hearingId: string, data: {
    judgeId: string;
    courtroomId: string;
    scheduledTime: string;
    durationMinutes: number;
    reason: string;
    litigantMessage?: string;
  }): Promise<Hearing> {
    return this.request<Hearing>(`/api/v1/hearings/${hearingId}/reassign`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  // --- Master Data & Judge Management ---
  async listJudges(): Promise<PageResponse<Judge>> {
    return this.request<PageResponse<Judge>>('/api/v1/judges?size=100');
  }

  async listCourtrooms(): Promise<PageResponse<Courtroom>> {
    return this.request<PageResponse<Courtroom>>('/api/v1/courtrooms?size=100');
  }

  async recordJudgeLeave(judgeId: string, data: { startDate: string; endDate: string; reason?: string }): Promise<any> {
    return this.request(`/api/v1/judges/${judgeId}/leave`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getJudgeLeaves(judgeId: string): Promise<any[]> {
    return this.request(`/api/v1/judges/${judgeId}/leaves`);
  }

  async deleteJudgeLeave(judgeId: string, leaveId: string): Promise<void> {
    return this.request(`/api/v1/judges/${judgeId}/leaves/${leaveId}`, {
      method: 'DELETE',
    });
  }

  // --- Scheduling Proposals ---
  async triggerSchedulingRun(config?: { horizonDays?: number; defaultDurationMinutes?: number }): Promise<SchedulingRun> {
    return this.request<SchedulingRun>('/api/v1/scheduling/run', {
      method: 'POST',
      body: JSON.stringify(config || {}),
    });
  }

  async getLatestSchedulingRun(): Promise<SchedulingRun> {
    return this.request<SchedulingRun>('/api/v1/scheduling/runs/latest');
  }

  async getLatestProposals(): Promise<Proposal[]> {
    return this.request<Proposal[]>('/api/v1/scheduling/proposals/latest');
  }

  async approveProposal(proposalId: string): Promise<Proposal> {
    return this.request<Proposal>(`/api/v1/scheduling/proposals/${proposalId}/approve`, {
      method: 'POST',
    });
  }

  async rejectProposal(proposalId: string, reason: string): Promise<Proposal> {
    return this.request<Proposal>(`/api/v1/scheduling/proposals/${proposalId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  }

  async applyManualOverride(data: {
    caseId: string;
    judgeId: string;
    courtroomId: string;
    scheduledTime: string;
    durationMinutes?: number;
    reason: string;
    overriddenBy: string;
  }): Promise<Proposal> {
    return this.request<Proposal>('/api/v1/scheduling/override', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // --- Admin ---
  async listAuditLogs(page = 0, size = 20, actor?: string, action?: string, entityType?: string, entityId?: string, from?: string, to?: string): Promise<PageResponse<AuditLogEntry>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });
    if (actor) params.append('actor', actor);
    if (action) params.append('action', action);
    if (entityType) params.append('entityType', entityType);
    if (entityId) params.append('entityId', entityId);
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    return this.request<PageResponse<AuditLogEntry>>(`/api/v1/admin/audit?${params.toString()}`);
  }

  async triggerNightlyBatch(triggerSource = 'DASHBOARD_UI', horizonDays?: number): Promise<{ jobExecutionId: number; message: string }> {
    const params = new URLSearchParams({ triggerSource });
    if (horizonDays) params.append('horizonDays', horizonDays.toString());
    return this.request<{ jobExecutionId: number; message: string }>(`/api/v1/admin/batch/reschedule?${params.toString()}`, {
      method: 'POST',
    });
  }

  async getBatchJobStatus(jobExecutionId: number): Promise<BatchJobStatusResponse> {
    return this.request<BatchJobStatusResponse>(`/api/v1/admin/batch/status/${jobExecutionId}`);
  }

  async getBatchScheduleDiff(runId: string): Promise<ScheduleDiffSummary> {
    return this.request<ScheduleDiffSummary>(`/api/v1/admin/batch/diff/${runId}`);
  }
}

export const api = new ApiClient();
