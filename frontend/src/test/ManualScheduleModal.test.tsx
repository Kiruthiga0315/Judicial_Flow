import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ManualScheduleModal } from '../components/ManualScheduleModal';
import { api } from '../api/client';

describe('ManualScheduleModal Component', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listJudges').mockResolvedValue({
      content: [
        { id: 'j-101', name: 'Hon. Justice Sharma', specialization: 'BAIL' },
        { id: 'j-102', name: 'Hon. Justice Patel', specialization: 'CIVIL' },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      last: true,
    });

    vi.spyOn(api, 'listCourtrooms').mockResolvedValue({
      content: [
        { id: 'cr-101', name: 'Courtroom 101', building: 'Main Block' },
        { id: 'cr-102', name: 'Courtroom 102', building: 'Annex' },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      last: true,
    });
  });

  it('renders case information, judge, courtroom selections and quick reason options', async () => {
    render(
      <ManualScheduleModal
        caseId="c-123"
        caseNumber="SYN-2026-TEST-99"
        caseType="BAIL"
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByText(/Direct Courtroom & Schedule Allocation/i)).toBeInTheDocument();
    expect(screen.getByText(/SYN-2026-TEST-99/i)).toBeInTheDocument();
    expect(screen.getByText(/BAIL/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Hon. Justice Sharma/i)).toBeInTheDocument();
      expect(screen.getByText(/Courtroom 101/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/Urgent Mention \/ Interim Relief allocation/i)).toBeInTheDocument();
  });

  it('populates reason when a quick reason button is clicked and submits override successfully', async () => {
    const applyOverrideSpy = vi.spyOn(api, 'applyManualOverride').mockResolvedValue({
      proposalId: 'p-1',
      caseId: 'c-123',
      caseNumber: 'SYN-2026-TEST-99',
      judgeId: 'j-101',
      judgeName: 'Hon. Justice Sharma',
      courtroomId: 'cr-101',
      courtroomName: 'Courtroom 101',
      proposedTime: '2026-10-15T10:00:00',
      durationMinutes: 60,
      status: 'OVERRIDDEN',
    });

    const onClose = vi.fn();
    const onSuccess = vi.fn();

    render(
      <ManualScheduleModal
        caseId="c-123"
        caseNumber="SYN-2026-TEST-99"
        caseType="BAIL"
        onClose={onClose}
        onSuccess={onSuccess}
      />
    );

    await waitFor(() => {
      expect(screen.getByText(/Hon. Justice Sharma/i)).toBeInTheDocument();
    });

    // Click quick reason button
    const quickBtn = screen.getByText(/Urgent Mention \/ Interim Relief allocation/i);
    fireEvent.click(quickBtn);

    const submitBtn = screen.getByRole('button', { name: /Allocate & Commit Hearing/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(applyOverrideSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          caseId: 'c-123',
          judgeId: 'j-101',
          courtroomId: 'cr-101',
          reason: 'Urgent Mention / Interim Relief allocation',
        })
      );
      expect(onSuccess).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('displays conflict message inline when server rejects with 409 conflict', async () => {
    const conflictError: any = new Error('Judge Hon. Justice Sharma is on approved leave on 2026-10-08');
    conflictError.status = 409;
    vi.spyOn(api, 'applyManualOverride').mockRejectedValue(conflictError);

    render(
      <ManualScheduleModal
        caseId="c-123"
        caseNumber="SYN-2026-TEST-99"
        caseType="BAIL"
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText(/Hon. Justice Sharma/i)).toBeInTheDocument();
    });

    // Click quick reason button
    const quickBtn = screen.getByText(/Special Bench direct assignment/i);
    fireEvent.click(quickBtn);

    const submitBtn = screen.getByRole('button', { name: /Allocate & Commit Hearing/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/Allocation Blocked/i)).toBeInTheDocument();
      expect(screen.getByText(/Judge Hon. Justice Sharma is on approved leave/i)).toBeInTheDocument();
    });
  });
});
