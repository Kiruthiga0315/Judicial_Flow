import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { AgingReportPage } from '../pages/AgingReportPage';
import { api } from '../api/client';

describe('AgingReportPage Component', () => {
  it('renders at-risk case metrics and urgency rankings', async () => {
    vi.spyOn(api, 'getAgingReport').mockResolvedValue([
      {
        caseId: 'case-99',
        caseNumber: 'BAIL-2026-999',
        caseType: 'BAIL',
        status: 'FILED',
        filingDate: '2026-08-01',
        daysPending: 65,
        adjournments: 4,
        statutoryDeadline: '2026-10-01',
        daysToDeadline: -4,
        priorityScore: 82.5,
      },
    ]);

    render(<AgingReportPage onSelectCase={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText('BAIL-2026-999')).toBeInTheDocument();
      expect(screen.getByText('82.5')).toBeInTheDocument();
      expect(screen.getByText(/65 days/i)).toBeInTheDocument();
      expect(screen.getByText(/Overdue/i)).toBeInTheDocument();
    });
  });
});
