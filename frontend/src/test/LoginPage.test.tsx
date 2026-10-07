import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LoginPage } from '../pages/LoginPage';
import * as AuthContextModule from '../context/AuthContext';

describe('LoginPage Component', () => {
  it('renders login form and dev role quick-fill buttons', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<LoginPage />);

    expect(screen.getByText(/JudicialFlow/i)).toBeInTheDocument();
    expect(screen.getByText(/Select Dev Role to Quick-Fill/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Registrar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Judge' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Admin' })).toBeInTheDocument();
  });

  it('populates fields when clicking dev role quick-fill button', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<LoginPage />);

    const registrarBtn = screen.getByRole('button', { name: 'Registrar' });
    fireEvent.click(registrarBtn);

    const userInput = screen.getByPlaceholderText(/e.g. registrar/i) as HTMLInputElement;
    expect(userInput.value).toBe('registrar');
  });

  it('calls login on form submission', async () => {
    const mockLogin = vi.fn().mockResolvedValue(undefined);
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: false,
      login: mockLogin,
      logout: vi.fn(),
    });

    render(<LoginPage />);

    const registrarBtn = screen.getByRole('button', { name: 'Registrar' });
    fireEvent.click(registrarBtn);

    const submitBtn = screen.getByRole('button', { name: /Access Court Registry/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('registrar', 'registrar123');
    });
  });
});
