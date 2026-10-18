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

  it('toggles password visibility when clicking eye button', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<LoginPage />);

    const passwordInput = screen.getByPlaceholderText('••••••••') as HTMLInputElement;
    expect(passwordInput.type).toBe('password');

    const toggleBtn = screen.getByRole('button', { name: /Show password/i });
    fireEvent.click(toggleBtn);
    expect(passwordInput.type).toBe('text');

    const hideBtn = screen.getByRole('button', { name: /Hide password/i });
    fireEvent.click(hideBtn);
    expect(passwordInput.type).toBe('password');
  });

  it('displays error banner when authentication fails', async () => {
    const mockLogin = vi.fn().mockRejectedValue(new Error('Invalid judicial credentials'));
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: false,
      login: mockLogin,
      logout: vi.fn(),
    });

    render(<LoginPage />);

    const adminBtn = screen.getByRole('button', { name: 'Admin' });
    fireEvent.click(adminBtn);

    const submitBtn = screen.getByRole('button', { name: /Access Court Registry/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid judicial credentials');
    });
  });

  it('renders loading state and disables submit button when isLoading is true', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<LoginPage />);

    const submitBtn = screen.getByRole('button', { name: /Access Court Registry/i });
    expect(submitBtn).toBeDisabled();
    expect(screen.getByText(/Validating Authentication.../i)).toBeInTheDocument();
  });

  it('navigates principles via carousel progress dots', () => {
    vi.spyOn(AuthContextModule, 'useAuth').mockReturnValue({
      user: null,
      role: null,
      judgeId: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<LoginPage />);

    expect(screen.getByText(/Every score shows its reasoning/i)).toBeInTheDocument();

    const dot2 = screen.getByRole('button', { name: /Go to principle 2/i });
    fireEvent.click(dot2);

    expect(screen.getByText(/Every override leaves an audit trail/i)).toBeInTheDocument();
  });
});
