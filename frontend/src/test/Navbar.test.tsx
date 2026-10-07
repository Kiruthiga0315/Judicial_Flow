import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Navbar } from '../components/Navbar';
import * as AuthContextModule from '../context/AuthContext';

describe('Navbar Role-Aware Navigation', () => {
  it('renders Admin navigation options (Audit Log, Nightly Batch) for ADMIN role', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: { authenticated: true, username: 'adminUser', roles: ['ROLE_ADMIN'] },
      role: 'ADMIN',
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<Navbar currentTab="cases" onSelectTab={vi.fn()} />);

    expect(screen.getAllByText(/Cases & Registry/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Proposed Allocations/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Audit Log/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Nightly Batch Engine/i)[0]).toBeInTheDocument();
  });

  it('hides Admin navigation options for REGISTRAR role', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: { authenticated: true, username: 'registrarUser', roles: ['ROLE_REGISTRAR'] },
      role: 'REGISTRAR',
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<Navbar currentTab="cases" onSelectTab={vi.fn()} />);

    expect(screen.getAllByText(/Cases & Registry/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/Proposed Allocations/i)[0]).toBeInTheDocument();
    expect(screen.queryByText(/Audit Log/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Nightly Batch Engine/i)).not.toBeInTheDocument();
  });

  it('hides proposals and admin pages for JUDGE role', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: { authenticated: true, username: 'judgeSharma', roles: ['ROLE_JUDGE'] },
      role: 'JUDGE',
      judgeId: 'judge-123',
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<Navbar currentTab="cases" onSelectTab={vi.fn()} />);

    expect(screen.getAllByText(/Cases & Registry/i)[0]).toBeInTheDocument();
    expect(screen.queryByText(/Proposed Allocations/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Audit Log/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Nightly Batch Engine/i)).not.toBeInTheDocument();
  });
});
