import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ReassignModal } from '../components/ReassignModal';
import { api } from '../api/client';
import { Hearing } from '../types/api';

const mockHearing: Hearing = {
  id: 'h-111',
  caseId: 'c-111',
  caseNumber: 'TEST-2026-001',
  judgeId: 'j-1',
  judgeName: 'Justice Initial',
  courtroomId: 'cr-1',
  courtroomName: 'Courtroom 1',
  scheduledTime: '2026-10-15T10:00:00',
  durationMinutes: 60,
  status: 'SCHEDULED',
};

describe('ReassignModal Component', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listJudges').mockResolvedValue({
      content: [
        { id: 'j-1', name: 'Justice Initial' },
        { id: 'j-2', name: 'Justice Next' },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      last: true,
    });

    vi.spyOn(api, 'listCourtrooms').mockResolvedValue({
      content: [
        { id: 'cr-1', name: 'Courtroom 1' },
        { id: 'cr-2', name: 'Courtroom 2' },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      last: true,
    });
  });

  it('renders hearing case number and current assignment details', async () => {
    render(<ReassignModal hearing={mockHearing} onClose={vi.fn()} onSuccess={vi.fn()} />);

    expect(screen.getByText(/TEST-2026-001/i)).toBeInTheDocument();
    expect(screen.getByText(/Manual Hearing Reassignment/i)).toBeInTheDocument();
  });

  it('displays conflict message inline when server responds with 409', async () => {
    const conflictError: any = new Error('Judge Justice Next is already booked');
    conflictError.status = 409;
    vi.spyOn(api, 'reassignHearing').mockRejectedValue(conflictError);

    render(<ReassignModal hearing={mockHearing} onClose={vi.fn()} onSuccess={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText(/Courtroom 1/i)).toBeInTheDocument();
    });

    // Fill in required reason
    const reasonInput = screen.getByPlaceholderText(/e.g. Urgent advocate mention/i);
    fireEvent.change(reasonInput, { target: { value: 'Advocate conflict resolution' } });

    // Submit form
    const form = screen.getByText(/Apply Override & Reassign/i).closest('form')!;
    fireEvent.submit(form);

    await waitFor(() => {
      expect(screen.getByText(/Reassignment Blocked/i)).toBeInTheDocument();
      expect(screen.getByText(/Schedule Conflict \(409\)/i)).toBeInTheDocument();
    });
  });
});
