import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { App } from '../App';
import * as AuthContextModule from '../context/AuthContext';
import { SchedulePage } from '../pages/SchedulePage';
import { CaseListPage } from '../pages/CaseListPage';
import { ProposalsPage } from '../pages/ProposalsPage';
import { CaseDetailDrawer } from '../components/CaseDetailDrawer';
import { ReassignModal } from '../components/ReassignModal';
import { api } from '../api/client';
import { Hearing, SchedulingRun, Case } from '../types/api';

describe('Route Guard Tests', () => {
  it('anonymous user is blocked and redirected to login page', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<App />);
    expect(screen.getByText(/Access Court Registry/i)).toBeInTheDocument();
    expect(screen.queryByText(/Cause List & Schedule/i)).not.toBeInTheDocument();
  });

  it('REGISTRAR is blocked from audit and batch pages in navigation and tab guards', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: { authenticated: true, username: 'registrar', roles: ['ROLE_REGISTRAR'] },
      role: 'REGISTRAR',
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<App />);
    // Audit and batch tabs not visible in Navbar
    expect(screen.queryByText(/Audit Log/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Nightly Batch Engine/i)).not.toBeInTheDocument();

    // Proposals visible for registrar
    expect(screen.getAllByText(/Proposed Allocations/i)[0]).toBeInTheDocument();
  });

  it('JUDGE is blocked from proposals and admin pages in navigation and tab guards', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: { authenticated: true, username: 'judge', roles: ['ROLE_JUDGE'] },
      role: 'JUDGE',
      judgeId: 'judge-1',
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<App />);
    // Blocked from proposals, audit, batch
    expect(screen.queryByText(/Proposed Allocations/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Audit Log/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Nightly Batch Engine/i)).not.toBeInTheDocument();

    // Can see judicial views
    expect(screen.getAllByText(/Cases & Registry/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Cause List & Schedule/i)[0]).toBeInTheDocument();
  });

  it('ADMIN reaches everything across all tabs and management views', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: { authenticated: true, username: 'admin', roles: ['ROLE_ADMIN'] },
      role: 'ADMIN',
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<App />);
    expect(screen.getAllByText(/Cases & Registry/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Aging & Urgency Report/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Cause List & Schedule/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Proposed Allocations/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Audit Log/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Nightly Batch Engine/i)[0]).toBeInTheDocument();
  });
});

describe('Feature and Role Guard Interaction Tests', () => {
  const mockHearing: Hearing = {
    id: 'h-test-1',
    caseId: 'c-test-1',
    caseNumber: 'TEST-2026-GUARD',
    judgeId: 'j-1',
    judgeName: 'Justice Sovereign',
    courtroomId: 'cr-1',
    courtroomName: 'Courtroom A',
    scheduledTime: '2026-10-15T10:00:00',
    durationMinutes: 45,
    status: 'SCHEDULED',
  };

  const mockCase: Case = {
    id: 'c-test-1',
    caseNumber: 'TEST-2026-GUARD',
    caseType: 'CIVIL',
    filingDate: '2026-01-01',
    currentStatus: 'SCHEDULED',
    priorityScore: 35.0,
    statutoryDeadline: null,
    priorAdjournments: 0,
    assignedJudge: { id: 'j-1', name: 'Justice Sovereign' },
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('JUDGE view hides all write controls on schedule and case detail drawer', async () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: { authenticated: true, username: 'judge', roles: ['ROLE_JUDGE'] },
      role: 'JUDGE',
      judgeId: 'j-1',
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    vi.spyOn(api, 'listJudges').mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    vi.spyOn(api, 'listCourtrooms').mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    vi.spyOn(api, 'listHearings').mockResolvedValue([mockHearing]);
    vi.spyOn(api, 'getCase').mockResolvedValue(mockCase);
    vi.spyOn(api, 'getCaseScore').mockResolvedValue({
      caseId: 'c-test-1',
      caseNumber: 'TEST-2026-GUARD',
      totalScore: 35.0,
      factors: [],
      summary: 'Normal priority',
      computedAt: '2026-10-01T00:00:00Z',
      persistedScoreId: 'sc-1',
    });
    vi.spyOn(api, 'getCaseScoreHistory').mockResolvedValue([]);
    vi.spyOn(api, 'getHearingForCase').mockResolvedValue(mockHearing);

    const onReassign = vi.fn();
    const { rerender } = render(<SchedulePage onSelectCase={vi.fn()} onOpenReassign={onReassign} />);

    await waitFor(() => {
      expect(screen.getByText('TEST-2026-GUARD')).toBeInTheDocument();
    });

    // Write actions must be hidden in schedule list for JUDGE
    expect(screen.queryByText(/Actions/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Reassign/i })).not.toBeInTheDocument();

    // In CaseDetailDrawer, Override / Reassign button must be hidden for JUDGE
    rerender(<CaseDetailDrawer caseId="c-test-1" onClose={vi.fn()} onReassignHearing={onReassign} />);

    await waitFor(() => {
      expect(screen.getByText(/Justice Sovereign/i)).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /Override \/ Reassign/i })).not.toBeInTheDocument();
  });

  it('case list sort triggers API calls with sort criteria', async () => {
    const listSpy = vi.spyOn(api, 'listCases').mockResolvedValue({
      content: [mockCase],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });

    render(<CaseListPage onSelectCase={vi.fn()} />);

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledWith(0, 15, 'filingDate', 'DESC', undefined, undefined, undefined);
    });

    // Click sort header for Case Number
    const caseNumberHeader = screen.getByText(/Case Number/i);
    fireEvent.click(caseNumberHeader);

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledWith(0, 15, 'caseNumber', 'DESC', undefined, undefined, undefined);
    });
  });

  it('case list filter triggers API calls with filter criteria', async () => {
    const listSpy = vi.spyOn(api, 'listCases').mockResolvedValue({
      content: [mockCase],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
      last: true,
    });

    render(<CaseListPage onSelectCase={vi.fn()} />);

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalled();
    });

    const typeSelect = screen.getByDisplayValue('All Types');
    fireEvent.change(typeSelect, { target: { value: 'BAIL' } });

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledWith(0, 15, 'filingDate', 'DESC', 'BAIL', undefined, undefined);
    });
  });

  it('proposals reject requires a reason before submission', async () => {
    const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const rejectSpy = vi.spyOn(api, 'rejectProposal').mockResolvedValue({} as any);

    const mockRun: SchedulingRun = {
      runId: 'r-1',
      triggeredAt: '2026-10-01T10:00:00Z',
      status: 'COMPLETED',
      totalCasesInput: 1,
      totalAssigned: 1,
      totalUnschedulable: 0,
      horizonDays: 14,
      proposals: [
        {
          proposalId: 'prop-1',
          caseId: 'c-1',
          caseNumber: 'PROP-2026-001',
          judgeId: 'j-1',
          judgeName: 'Justice Alpha',
          courtroomId: 'cr-1',
          courtroomName: 'Courtroom 1',
          slotStartTime: '2026-10-10T10:00:00Z',
          durationMinutes: 60,
          scoreAtScheduling: 80.0,
          status: 'PROPOSED',
          explanation: 'Top priority matter',
        },
      ],
    };

    vi.spyOn(api, 'getLatestSchedulingRun').mockResolvedValue(mockRun);

    render(<ProposalsPage onSelectCase={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText('PROP-2026-001')).toBeInTheDocument();
    });

    // Open reject modal
    fireEvent.click(screen.getByRole('button', { name: /Reject Proposal/i }));
    expect(screen.getByText(/Reject Proposed Allocation/i)).toBeInTheDocument();

    // Submit form with empty reason via form submit event
    const form = screen.getByRole('button', { name: /Confirm Rejection/i }).closest('form')!;
    fireEvent.submit(form);

    expect(alertMock).toHaveBeenCalledWith('A rejection reason is required for judicial registry logs.');
    expect(rejectSpy).not.toHaveBeenCalled();
  });

  it('proposals show 409 inline on assignment conflict', async () => {
    const conflictError: any = new Error('Judge is already booked in another courtroom');
    conflictError.status = 409;
    vi.spyOn(api, 'approveProposal').mockRejectedValue(conflictError);

    const mockRun: SchedulingRun = {
      runId: 'r-1',
      triggeredAt: '2026-10-01T10:00:00Z',
      status: 'COMPLETED',
      totalCasesInput: 1,
      totalAssigned: 1,
      totalUnschedulable: 0,
      horizonDays: 14,
      proposals: [
        {
          proposalId: 'prop-1',
          caseId: 'c-1',
          caseNumber: 'PROP-409-TEST',
          judgeId: 'j-1',
          judgeName: 'Justice Conflict',
          courtroomId: 'cr-1',
          courtroomName: 'Courtroom 1',
          slotStartTime: '2026-10-10T10:00:00Z',
          durationMinutes: 60,
          scoreAtScheduling: 75.0,
          status: 'PROPOSED',
        },
      ],
    };

    vi.spyOn(api, 'getLatestSchedulingRun').mockResolvedValue(mockRun);

    render(<ProposalsPage onSelectCase={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText('PROP-409-TEST')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Approve & Commit/i }));

    await waitFor(() => {
      expect(screen.getByText(/Assignment Conflict/i)).toBeInTheDocument();
      expect(screen.getByText(/Conflict \(409\): Judge is already booked in another courtroom/i)).toBeInTheDocument();
    });
  });

  it('reassign modal requires reason before submission', async () => {
    const reassignSpy = vi.spyOn(api, 'reassignHearing').mockResolvedValue({} as any);
    vi.spyOn(api, 'listJudges').mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    vi.spyOn(api, 'listCourtrooms').mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });

    render(<ReassignModal hearing={mockHearing} onClose={vi.fn()} onSuccess={vi.fn()} />);

    // Submit form without filling reason via form submit event
    const form = screen.getByRole('button', { name: /Apply Override & Reassign/i }).closest('form')!;
    fireEvent.submit(form);

    await waitFor(() => {
      expect(screen.getByText(/A detailed administrative justification is required for judicial audit logs\./i)).toBeInTheDocument();
    });
    expect(reassignSpy).not.toHaveBeenCalled();
  });

  it('reassign modal shows 409 inline on conflict error', async () => {
    const conflictError: any = new Error('Courtroom double-booking conflict detected');
    conflictError.status = 409;
    vi.spyOn(api, 'reassignHearing').mockRejectedValue(conflictError);
    vi.spyOn(api, 'listJudges').mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });
    vi.spyOn(api, 'listCourtrooms').mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, last: true });

    render(<ReassignModal hearing={mockHearing} onClose={vi.fn()} onSuccess={vi.fn()} />);

    const reasonInput = screen.getByPlaceholderText(/e\.g\. Urgent advocate mention/i);
    fireEvent.change(reasonInput, { target: { value: 'Valid administrative reason' } });

    const form = screen.getByRole('button', { name: /Apply Override & Reassign/i }).closest('form')!;
    fireEvent.submit(form);

    await waitFor(() => {
      expect(screen.getByText(/Reassignment Blocked/i)).toBeInTheDocument();
      expect(screen.getByText(/Schedule Conflict \(409\): Courtroom double-booking conflict detected/i)).toBeInTheDocument();
    });
  });
});
