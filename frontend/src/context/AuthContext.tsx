import React, { createContext, useContext, useState, useEffect } from 'react';
import { AuthUser, UserRole } from '../types/api';
import { api } from '../api/client';

interface AuthContextType {
  user: AuthUser | null;
  role: UserRole | null;
  judgeId: string | null;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const logout = () => {
    api.clearCredentials();
    setUser(null);
  };

  useEffect(() => {
    // Register 401 handler so expired/invalid in-memory sessions cleanly return to login
    api.onUnauthorized(logout);
  }, []);

  const login = async (username: string, password: string) => {
    setIsLoading(true);
    try {
      api.setCredentials(username, password);
      const authUser = await api.getMe();
      setUser(authUser);
    } catch (err) {
      api.clearCredentials();
      setUser(null);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const role: UserRole | null = user
    ? user.roles.includes('ROLE_ADMIN')
      ? 'ADMIN'
      : user.roles.includes('ROLE_REGISTRAR')
      ? 'REGISTRAR'
      : user.roles.includes('ROLE_JUDGE')
      ? 'JUDGE'
      : null
    : null;

  const judgeId = user?.judgeId || null;

  return (
    <AuthContext.Provider value={{ user, role, judgeId, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
